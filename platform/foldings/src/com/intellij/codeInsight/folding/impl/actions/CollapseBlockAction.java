// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.folding.CollapseBlockHandler;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class CollapseBlockAction extends BaseCodeInsightAction {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
        executor(project, editor, psiFile, true);
      }
    };
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return executor(project, editor, psiFile, false);
  }

  private static boolean executor(final @NotNull Project project,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  boolean executeAction) {
    final InjectedLanguageManager instance = InjectedLanguageManager.getInstance(project);
    while (true) {
      final List<CollapseBlockHandler> handlers = CollapseBlockHandler.EP_NAME.allForLanguage(file.getLanguage());
      if (handlers.isEmpty()) {
        if (!instance.isInjectedFragment(file) || !(editor instanceof EditorWindow)) {
          return false;
        }
        file = instance.getTopLevelFile(file);
        if (file == null) {
          return false;
        }
        editor = ((EditorWindow)editor).getDelegate();
        continue;
      }
      if (executeAction) {
        final Editor finalEditor = editor;
        final PsiFile finalFile = file;
        handlers.forEach(handler -> handler.invoke(project, finalEditor, finalFile));
      }
      return true;
    }
  }
}
