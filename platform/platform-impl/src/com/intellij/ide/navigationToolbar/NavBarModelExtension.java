// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Collection;

/**
 * The interface has a default implementation ({@link DefaultNavBarExtension}) which is normally registered as last.
 * That means that custom implementations are called before the default one - with the exception of {@link #adjustElement(PsiElement)}
 * method, for which the order is reverse.
 *
 * @author anna
 */
public interface NavBarModelExtension {
  ExtensionPointName<NavBarModelExtension> EP_NAME = ExtensionPointName.create("com.intellij.navbar");

  @ApiStatus.Internal
  Key<Boolean> IGNORE_IN_NAVBAR = Key.create("IGNORE_IN_NAVBAR");

  default @Nullable Icon getIcon(Object object) { return null; }

  default @Nullable @Nls String getPresentableText(Object object, boolean forPopup) {
    return getPresentableText(object);
  }

  @Nullable
  @Nls
  String getPresentableText(Object object);

  @Nullable
  PsiElement getParent(PsiElement psiElement);

  @Nullable
  PsiElement adjustElement(@NotNull PsiElement psiElement);

  @NotNull
  Collection<VirtualFile> additionalRoots(Project project);

  default void uiDataSnapshot(@NotNull DataSink sink, @NotNull DataSnapshot snapshot) {
  }

  /**
   * @noinspection unused
   * @deprecated Unused. Use {@link #uiDataSnapshot} instead
   */
  @Deprecated(forRemoval = true)
  default @Nullable Object getData(@NotNull String dataId, @NotNull DataProvider provider) { return null; }

  /** @noinspection LambdaUnfriendlyMethodOverload*/
  default @Nullable String getPopupMenuGroup(@NotNull DataContext dataContext) { return null; }

  /**
   * @noinspection LambdaUnfriendlyMethodOverload, unused
   * @deprecated Unused. Use {@link #uiDataSnapshot} instead
   */
  @Deprecated(forRemoval = true)
  default @Nullable String getPopupMenuGroup(@NotNull DataProvider provider) { return null; }

  /** @noinspection LambdaUnfriendlyMethodOverload*/
  default PsiElement getLeafElement(@NotNull DataMap dataProvider) {
    return getLeafElement((DataContext)dataId -> dataProvider.get(DataKey.create(dataId)));
  }

  /**
   * @noinspection LambdaUnfriendlyMethodOverload, unused
   * @deprecated Use {@link #getLeafElement(DataMap)} instead
   */
  @Deprecated(forRemoval = true)
  default PsiElement getLeafElement(@NotNull DataContext dataContext) {
    return null;
  }

  default boolean processChildren(Object object, Object rootElement, Processor<Object> processor) {
    return true;
  }

  default boolean normalizeChildren() {
    return true;
  }

  @ApiStatus.Internal
  default Boolean shouldExpandOnClick(PsiElement psiElement) {
    return null;
  }
}
