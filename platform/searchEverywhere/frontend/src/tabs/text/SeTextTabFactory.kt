// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.impl.TextSearchContributor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabFactory
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import fleet.kernel.DurableRef
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextTabFactory : SeTabFactory {
  override fun getTab(project: Project?, sessionRef: DurableRef<SeSessionEntity>, dataContext: DataContext): SeTab? {
    if (project == null || !TextSearchContributor.enabled()) return null

    val delegate = SeTabDelegate(project,
                                 sessionRef,
                                 "Text",
                                 listOf(SeProviderId(SeProviderIdUtils.TEXT_ID)),
                                 dataContext)
    return SeTextTab(delegate)
  }
}