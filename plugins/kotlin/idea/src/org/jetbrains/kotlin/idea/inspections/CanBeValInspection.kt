// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.cfg.containingDeclarationForPseudocode
import org.jetbrains.kotlin.cfg.pseudocode.Pseudocode
import org.jetbrains.kotlin.cfg.pseudocode.PseudocodeUtil
import org.jetbrains.kotlin.cfg.pseudocode.instructions.Instruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.InstructionWithNext
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.AccessTarget
import org.jetbrains.kotlin.cfg.pseudocode.instructions.eval.WriteValueInstruction
import org.jetbrains.kotlin.cfg.pseudocode.instructions.special.LocalFunctionDeclarationInstruction
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class CanBeValInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            private val pseudocodeCache = HashMap<KtDeclaration, Pseudocode>()
            override fun visitDeclaration(declaration: KtDeclaration) {
                super.visitDeclaration(declaration)
                if (declaration is KtValVarKeywordOwner && Util.canBeVal(declaration, pseudocodeCache, ignoreNotUsedVals = true)) {
                    reportCanBeVal(declaration)
                }
            }

            private fun reportCanBeVal(declaration: KtValVarKeywordOwner) {
                val keyword = declaration.valOrVarKeyword!!
                val problemDescriptor = holder.manager.createProblemDescriptor(
                    keyword,
                    keyword,
                    KotlinBundle.message("variable.is.never.modified.and.can.be.declared.immutable.using.val"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly,
                    ChangeVariableMutabilityFix(declaration, false).asQuickFix(),
                )
                holder.registerProblem(problemDescriptor)
            }
        }
    }

    object Util {
        fun canBeVal(
            declaration: KtDeclaration,
            pseudocodeCache: HashMap<KtDeclaration, Pseudocode> = HashMap(),
            ignoreNotUsedVals: Boolean
        ): Boolean {
            when (declaration) {
                is KtProperty -> {
                    if (declaration.isVar && declaration.isLocal && !declaration.hasModifier(KtTokens.LATEINIT_KEYWORD) &&
                        canBeVal(
                            declaration,
                            declaration.hasInitializer() || declaration.hasDelegateExpression(),
                            listOf(declaration),
                            ignoreNotUsedVals,
                            pseudocodeCache
                        )
                    ) {
                        return true
                    }
                }

                is KtDestructuringDeclaration -> {
                    val entries = declaration.entries
                    if (declaration.isVar && entries.all { canBeVal(it, true, entries, ignoreNotUsedVals, pseudocodeCache) }) {
                        return true
                    }
                }
            }
            return false
        }

        private fun canBeVal(
            declaration: KtVariableDeclaration,
            hasInitializerOrDelegate: Boolean,
            allDeclarations: Collection<KtVariableDeclaration>,
            ignoreNotUsedVals: Boolean,
            pseudocodeCache: MutableMap<KtDeclaration, Pseudocode>
        ): Boolean {
            if (ignoreNotUsedVals && allDeclarations.all { ReferencesSearch.search(it, it.useScope).asIterable().none() }) {
                // do not report for unused var's (otherwise we'll get it highlighted immediately after typing the declaration
                return false
            }

            return if (hasInitializerOrDelegate) {
                val hasWriteUsages = ReferencesSearch.search(declaration, declaration.useScope).asIterable().any {
                    (it as? KtSimpleNameReference)?.element?.readWriteAccess(useResolveForReadWrite = true)?.isWrite == true
                }
                !hasWriteUsages
            } else {
                val bindingContext = declaration.analyze(BodyResolveMode.FULL)
                val pseudocode = pseudocode(declaration, bindingContext, pseudocodeCache) ?: return false
                val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration] ?: return false

                val writeInstructions = pseudocode.collectWriteInstructions(descriptor)
                if (writeInstructions.isEmpty()) return false // incorrect code - do not report

                writeInstructions.none { it.owner !== pseudocode || canReach(it, writeInstructions) }
            }
        }

        private fun pseudocode(
            element: KtElement,
            bindingContext: BindingContext,
            pseudocodeCache: MutableMap<KtDeclaration, Pseudocode>
        ): Pseudocode? {
            val declaration = element.containingDeclarationForPseudocode ?: return null
            return pseudocodeCache.getOrPut(declaration) { PseudocodeUtil.generatePseudocode(declaration, bindingContext) }
        }

        private fun Pseudocode.collectWriteInstructions(descriptor: DeclarationDescriptor): Set<WriteValueInstruction> =
            with(instructionsIncludingDeadCode) {
                filterIsInstance<WriteValueInstruction>()
                    .asSequence()
                    .filter { (it.target as? AccessTarget.Call)?.resolvedCall?.resultingDescriptor == descriptor }
                    .toSet() +

                        filterIsInstance<LocalFunctionDeclarationInstruction>()
                            .map { it.body.collectWriteInstructions(descriptor) }
                            .flatten()
            }

        private fun canReach(
            from: Instruction,
            targets: Set<Instruction>,
            visited: HashSet<Instruction> = HashSet()
        ): Boolean {
            // special algorithm for linear code to avoid too deep recursion
            var instruction = from
            while (instruction is InstructionWithNext) {
                if (instruction is LocalFunctionDeclarationInstruction) {
                    if (canReach(instruction.body.enterInstruction, targets, visited)) return true
                }
                val next = instruction.next ?: return false
                if (next in visited) return false
                if (next in targets) return true
                visited.add(next)
                instruction = next
            }

            for (next in instruction.nextInstructions) {
                if (next in visited) continue
                if (next in targets) return true
                visited.add(next)
                if (canReach(next, targets, visited)) return true
            }
            return false
        }
    }

}