// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;

public final class PropertiesElementFactory {
  private static final Key<PropertiesFile> SYSTEM_PROPERTIES_KEY = Key.create("system.properties.file");

  private static final UserDataCache<PropertiesFile,Project,Void> PROPERTIES = new UserDataCache<>("system.properties.file") {
    @Override
    protected PropertiesFile compute(Project project, Void p) {
      return createPropertiesFile(project, System.getProperties(), "system");
    }
  };

  public static @NotNull IProperty createProperty(@NotNull Project project,
                                                  @NonNls @NotNull String name,
                                                  @NonNls @NotNull String value,
                                                  @Nullable Character delimiter) {
    return createProperty(project, name, value, delimiter, PropertyKeyValueFormat.PRESENTABLE);
  }

  public static @NotNull IProperty createProperty(@NotNull Project project,
                                                  @NonNls @NotNull String name,
                                                  @NonNls @NotNull String value,
                                                  @Nullable Character delimiter,
                                                  @NotNull PropertyKeyValueFormat format) {
    String text = getPropertyText(name, value, delimiter, project, format);
    final PropertiesFile dummyFile = createPropertiesFile(project, text);
    IProperty iProperty = dummyFile.getProperties().get(0);
    if (iProperty instanceof Property property) {
      CodeStyleManager.getInstance(project).reformat(property);
    }
    return iProperty;
  }

  public static @NotNull String getPropertyText(@NonNls @NotNull String name,
                                                @NonNls @NotNull String value,
                                                @NonNls @Nullable Character delimiter,
                                                @Nullable Project project,
                                                @NotNull PropertyKeyValueFormat format) {
    if (delimiter == null) {
      delimiter = project == null ? '=' : PropertiesCodeStyleSettings.getInstance(project).getDelimiter();
    }
    return (format != PropertyKeyValueFormat.FILE ? escape(name) : name) + delimiter + escapeValue(value, delimiter, format);
  }

  public static @NotNull PropertiesFile createPropertiesFile(@NotNull Project project, @NonNls @NotNull String text) {
    @NonNls String filename = "dummy." + PropertiesFileType.INSTANCE.getDefaultExtension();
    return (PropertiesFile)PsiFileFactory.getInstance(project)
      .createFileFromText(filename, PropertiesFileType.INSTANCE, text);
  }

  public static @NotNull PropertiesFile createPropertiesFile(@NotNull Project project, Properties properties, String fileName) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
      properties.store(stream, "");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    @NonNls String filename = fileName + "." + PropertiesFileType.INSTANCE.getDefaultExtension();
    return (PropertiesFile)PsiFileFactory.getInstance(project)
      .createFileFromText(filename, PropertiesFileType.INSTANCE, stream.toString());
  }

  public static synchronized @NotNull PropertiesFile getSystemProperties(@NotNull Project project) {
    PropertiesFile systemPropertiesFile = project.getUserData(SYSTEM_PROPERTIES_KEY);
    if (systemPropertiesFile == null) {
      project.putUserData(SYSTEM_PROPERTIES_KEY, systemPropertiesFile = createPropertiesFile(project, System.getProperties(), "system"));
    }
    return systemPropertiesFile;
  }

  private static @NotNull String escape(@NotNull String name) {
    if (StringUtil.startsWithChar(name, '#') || StringUtil.startsWithChar(name, '!')) {
      name = "\\" + name;
    }
    return StringUtil.escapeChars(name, '=', ':', ' ', '\t');
  }

  /**
   * @deprecated use {@link #escapeValue(String, char, PropertyKeyValueFormat)} instead
   */
  @Deprecated(forRemoval = true)
  public static String escapeValue(String value, char delimiter) {
    return escapeValue(value, delimiter, PropertyKeyValueFormat.PRESENTABLE);
  }

  public static String escapeValue(String value, char delimiter, PropertyKeyValueFormat format) {
    return PropertiesResourceBundleUtil.convertValueToFileFormat(value, delimiter, format);
  }
}
