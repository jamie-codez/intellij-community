/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeHighlighting;

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Should be implemented to include corresponding pass in code smells check
 */
public interface MainHighlightingPassFactory extends TextEditorHighlightingPassFactory {
  @Nullable
  TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile psiFile,
                                                        @NotNull Document document,
                                                        @NotNull HighlightInfoProcessor highlightInfoProcessor);
}
