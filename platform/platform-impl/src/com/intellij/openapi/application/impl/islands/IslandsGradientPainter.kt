// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.GradientTextureCache
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.wm.IdeFrame
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.RenderingHints

internal class IslandsGradientPainter(private val frame: IdeFrame, private val endColor: Color) : AbstractPainter() {
  private val gradientCache: GradientTextureCache = GradientTextureCache()

  private val projectWindowCustomizer = ProjectWindowCustomizerService.getInstance()

  private var doPaint = true

  override fun needsRepaint(): Boolean = true

  override fun executePaint(component: Component, g: Graphics2D) {
    if (doPaint) {
      try {
        doPaint = false
        doPaint(component, g)
      }
      finally {
        doPaint = true
      }
    }
  }

  private fun doPaint(component: Component, g: Graphics2D) {
    val startColor = projectWindowCustomizer.getGradientProjectColor(frame.project ?: return)

    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

    g.paint = gradientCache.getVerticalTexture(g, component.height, startColor, endColor, 0, 0)
    g.fillRect(0, 0, component.width, component.height)
  }
}