// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.javadoc.DocumentationDelegateProvider
import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import java.util.concurrent.Callable
import kotlin.test.assertContains
import kotlin.test.assertFails

class JavaDocumentationTest : LightJavaCodeInsightFixtureTestCase() {
  fun testConstructorJavadoc() {
    configure("""\
      class Foo { Foo() {} Foo(int param) {} }

      class Foo2 {{
        new Foo<caret>
      }}""".trimIndent())

    val originalElement = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    val element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
    val doc = JavaDocumentationProvider().generateDoc(element, originalElement)

    val expected =
      "<html>" +
      "Candidates for new <b>Foo</b>() are:<br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo()\">Foo()</a><br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo(int)\">Foo(int param)</a><br>" +
      "</html>"

    assertEquals(expected, doc)
  }

  fun testConstructorJavadoc2() {
    configure("""
      class Foo { Foo() {} Foo(int param) {} }

      class Foo2 {{
        new Foo(<caret>)
      }}""".trimIndent())

    val elementAt = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
    val exprList = PsiTreeUtil.getParentOfType(elementAt, PsiExpressionList::class.java)
    val doc = JavaDocumentationProvider().generateDoc(exprList, elementAt)

    val expected =
      "<html>" +
      "Candidates for new <b>Foo</b>() are:<br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo()\">Foo()</a><br>" +
      "&nbsp;&nbsp;<a href=\"psi_element://Foo#Foo(int)\">Foo(int param)</a><br>" +
      "</html>"

    assertEquals(expected, doc)
  }

  fun testMethodDocWhenInArgList() {
    configure("""
      class Foo { void doFoo() {} }

      class Foo2 {{
        new Foo().doFoo(<caret>)
      }}""".trimIndent())

    val exprList = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset),
                                               PsiExpressionList::class.java)
    val doc = JavaDocumentationProvider().generateDoc(exprList, null)

    val expected =
      "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://Foo\"><code><span style=\"color:#000000;\">Foo</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">doFoo</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><table class='sections'><p></table>"

    assertEquals(expected, doc)
  }

  fun testPatternDoc() {
    configure("""
      /**
       *  @param i my parameter
       */ 
      record Rec(int i, String s) {
        void foo(Object o) {
          switch (o) {
            case Rec(int patternName1, String patternName2) -> {
              <caret>patternName1            
            }      
          }
        }
      }""".trimIndent())

    val ref = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset),
                                               PsiReferenceExpression::class.java)!!
    val doc = JavaDocumentationProvider().generateDoc(ref.resolve(), null)

    val expected =
      "<div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">int</span> <span style=\"color:#000000;\">patternName1</span></pre></div><div class='content'>my parameter</div>"

    assertEquals(expected, doc)
  }

  fun testRecordComponent() {
    configure("""
      /**
       * @param foo doc for foo
       * @param bar doc for bar
       */
      public record Rec(int <caret> foo, int bar) {
      }
      """.trimIndent())

    val recordComponent = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset),
                                               PsiRecordComponent::class.java)!!
    val doc = JavaDocumentationProvider().generateDoc(recordComponent, null)

    val expected =
      "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://Rec\"><code><span style=\"color:#000000;\">Rec</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">int</span> <span style=\"color:#000000;\">foo</span></pre></div><div class='content'>doc for foo  </div>"

    assertEquals(expected, doc)
  }

  fun testGenericMethod() {
    doTestCtrlHoverDoc(
      """\
      class Bar<T> { java.util.List<T> foo(T param); }

      class Foo {{
        new Bar<String>().f<caret>oo();
      }}""",
      """<span style="color:#000000;"><a href="psi_element://Bar">Bar</a></span><br/> <span style="color:#000000;"><a href="psi_element://java.util.List">List</a></span><span style="">&lt;</span><span style="color:#000000;">String</span><span style="">&gt;</span> <span style="color:#000000;">foo</span><span style="">(</span><span style="color:#000000;">String</span> <span style="color:#000000;">param</span><span style="">)</span>"""
    )
  }

  fun testGenericField() {
    doTestCtrlHoverDoc(
      """\
      class Bar<T> { T field; }

      class Foo {{
        new Bar<Integer>().fi<caret>eld
      }}""",

      """<span style="color:#000000;"><a href="psi_element://Bar">Bar</a></span><br/> <span style="color:#000000;">Integer</span> <span style="color:#660e7a;font-weight:bold;">field</span>"""
    )
  }

  fun testMethodInAnonymousClass() {
    doTestCtrlHoverDoc(
      """\
      class Foo {{
        new Runnable() {
          @Override
          public void run() {
            <caret>m();
          }

          private void m() {}
        }.run();
      }}""",
      "<span style=\"color:#000080;font-weight:bold;\">private</span> <span style=\"color:#000080;font-weight:bold;\">void</span> <span style=\"color:#000000;\">m</span><span style=\"\">(</span><span style=\"\">)</span>"
    )
  }


  fun testAnnotationInCtrlHoverDoc() {
    doTestCtrlHoverDoc(
      """\
      class Foo {
          {<caret>m();}
          @Anno
          private void m() {}
      }
      @java.lang.annotation.Documented
      @interface Anno {} """,
      """<span style="color:#000000;"><a href="psi_element://Foo">Foo</a></span><br/> <span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Anno">Anno</a></span>&nbsp;<br/><span style="color:#000080;font-weight:bold;">private</span> <span style="color:#000080;font-weight:bold;">void</span> <span style="color:#000000;">m</span><span style="">(</span><span style="">)</span>"""
    )
  }

  fun testInnerClass() {
    doTestCtrlHoverDoc(
      """\
      class C {
        Outer.Inner field;
        
        void m() {
          <caret>field.hashCode();
        }
      }
      class Outer {
        class Inner {}
      }""",
      """<span style="color:#000000;"><a href="psi_element://C">C</a></span><br/> <span style="color:#000000;"><a href="psi_element://Outer.Inner">Outer.Inner</a></span> <span style="color:#660e7a;font-weight:bold;">field</span>"""
    )
  }

  fun testAsterisksFiltering() {
    configure("""
      class C {
        /**
         * For example, {@link String#String(byte[],
         * int, int,
         * String)}.
         */
        public void <caret>m() { }
      }
    """.trimIndent())

    val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
    val doc = JavaDocumentationProvider().generateDoc(method, null)

    val expected = "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://C\"><code><span style=\"color:#000000;\">C</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">public</span>&nbsp;<span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">m</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><div class='content'>\n  For example, <a href=\"psi_element://java.lang.String#String(byte[], int, int, java.lang.String)\"><code><span style=\"color:#0000ff;\">String</span><span style=\"\">.</span><span style=\"color:#0000ff;\">String</span><span style=\"\">(</span><span style=\"color:#000080;font-weight:bold;\">byte</span><span style=\"\">[],&#32;</span><span style=\"color:#000080;font-weight:bold;\">int</span><span style=\"\">,&#32;</span><span style=\"color:#000080;font-weight:bold;\">int</span><span style=\"\">,&#32;String)</span></code></a>.\n   </div><table class='sections'></table>"
    TestCase.assertEquals(expected, doc)
  }

  fun testInlineTagSpacing() {
    configure("""
      class C {
        /** Visit the "{@code /login}" URL. */
        public void <caret>m() { }
      }
    """.trimIndent())

    val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
    val doc = JavaDocumentationProvider().generateDoc(method, null)

    val expected = "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://C\"><code><span style=\"color:#000000;\">C</span></code></a></div><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">public</span>&nbsp;<span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">m</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><div class='content'> Visit the \"<code><span style=\"\">/login</span></code>\" URL. </div><table class='sections'></table>"
    TestCase.assertEquals(expected, doc)
  }

  fun testInlineReturnJava16() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_16) {
      configure("""
      class C {
        /** {@return smth} */
        public String <caret>m() { }
      }
    """.trimIndent())
      val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
      val doc = JavaDocumentationProvider().generateRenderedDoc(method!!.docComment!!)
      val expected = "<div class='content'> Returns smth. </div><table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><p> smth</td></table>"
      TestCase.assertEquals(expected, doc)
    }
  }


  fun testMethodToMethodDelegate() {
    val provider: DocumentationDelegateProvider = object : DocumentationDelegateProvider() {
      override fun computeDocumentationDelegate(member: PsiMember): PsiDocCommentOwner? {
        if (member is PsiMethod && member.name == "foo") {
          return JavaPsiFacade.getInstance(project).findClass("Foo", member.getResolveScope())!!.findMethodBySignature(member, false)
        }
        return null
      }
    }
    DocumentationDelegateProvider.EP_NAME.point.registerExtension(provider, myFixture.testRootDisposable)
    configure("""
      class Foo {
        /**
        * Some doc
        */
        void foo() {}
      }

      class Bar {
        void fo<caret>o() {}
      }
    """.trimIndent())

    val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
    val doc = JavaDocumentationProvider().generateDoc(method, null)
    val expected = "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://Bar\"><code><span style=\"color:#000000;\">Bar</span></code></a></div><div class=\'definition\'><pre><span style=\"color:#000080;font-weight:bold;\">void</span>&nbsp;<span style=\"color:#000000;\">foo</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><table class=\'sections\'><p><tr><td valign=\'top\' class=\'section\'><p>From class:</td><td valign=\'top\'><p><a href=\"psi_element://Foo\"><code><span style=\"color:#000000;\">Foo</span></code></a><br>\n  Some doc\n  </td></table>"

    TestCase.assertEquals(expected, doc)
  }

  fun `test at method name with overloads`() {
    val input = """
      class Foo {
        void foo(String s) {
          s.region<caret>Matches()
        } 
      }
    """.trimIndent()

    val actual = JavaExternalDocumentationTest.getDocumentationText(myFixture.project, input)

    val expected = "<html><head></head><body><div class=\"content\"><p>Candidates for method call <b>s.<wbr>regionMatches()</b> are:<br>" +
                   "<br>" +
                   "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)\">boolean regionMatches(int, String, int, int)</a><br>" +
                   "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(boolean, int, java.lang.String, int, int)\">boolean regionMatches(boolean, int, String, int, int)</a><br>" +
                   "</p></div></body></html>"

    TestCase.assertEquals(expected, actual)
  }

  fun `test navigation updates decoration`() {
    val input = """
      class Foo {
        void foo(String s) {
          s.region<caret>Matches()
        } 
      }
    """.trimIndent()

    val documentationManager = DocumentationManager.getInstance(myFixture.project)
    JavaExternalDocumentationTest.getDocumentationText(myFixture.project, input) { component ->
      val expected = "<html><head></head><body><div class=\"content\"><p>Candidates for method call <b>s.<wbr>regionMatches()</b> are:<br>" +
                     "<br>" +
                     "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)\">boolean regionMatches(int, String, int, int)</a><br>" +
                     "&nbsp;&nbsp;<a href=\"psi_element://java.lang.String#regionMatches(boolean, int, java.lang.String, int, int)\">boolean regionMatches(boolean, int, String, int, int)</a><br>" +
                     "</p></div></body></html>"
      assertEquals(expected, component.decoratedText)

      documentationManager.navigateByLink(component, null, "psi_element://java.lang.String#regionMatches(int, java.lang.String, int, int)")
      try {
        JavaExternalDocumentationTest.waitTillDone(documentationManager.lastAction)
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }

      // Here we check that the covering module (SDK in this case) is rendered in decorated info
      assertTrue(
        component.decoratedText.contains("<div class=\"bottom\"><icon src=\"AllIcons.Nodes.PpLibFolder\" />&nbsp;&lt; java 1.7 &gt;</div>"))
      return@getDocumentationText null
    }
  }

  fun testBlockquotePre() {
    configure("""
      class C {
        /** 
         * <p> Examples of expected usage:
         * <blockquote><pre>
         *   StringBuilder sb = new StringBuilder();
         * </pre></blockquote>
         * <pre><code>StringBuilder sb = new StringBuilder();</code></pre>
         * <p> Continuing...
         * <blockquote><pre>
         *   quote nr&nbsp;2;</pre></blockquote>
         * <p> Continuing...
         * <blockquote><pre>
         *   (this.charAt(<i>k</i>) == ch) {@code &&} (<i>k</i> &lt;= fromIndex)
         * </pre></blockquote>
         * <blockquote><pre>
         *   Unfinished blockquote
         * </pre> </blockquote>
        */
        public void <caret>m() { }
      }
    """.trimIndent())

    val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
    val doc = JavaDocumentationProvider().generateDoc(method, null)

    val expected = """
      <div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://C"><code><span style="color:#000000;">C</span></code></a></div><div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span>&nbsp;<span style="color:#000080;font-weight:bold;">void</span>&nbsp;<span style="color:#000000;">m</span><span style="">(</span><span style="">)</span></pre></div><div class='content'> 
        <p> Examples of expected usage:
        <blockquote><pre><span style="">StringBuilder&#32;sb&#32;=&#32;</span><span style="color:#000080;font-weight:bold;">new&#32;</span><span style="">StringBuilder();</span></pre></blockquote>
        <pre><code><span style="">StringBuilder&#32;sb&#32;=&#32;</span><span style="color:#000080;font-weight:bold;">new&#32;</span><span style="">StringBuilder();</span></code></pre>
        <p> Continuing...
        <blockquote><pre><span style="">quote&#32;nr&#32;</span><span style="color:#0000ff;">2</span><span style="">;</span></pre></blockquote>
        <p> Continuing...
        <blockquote><pre>
          (this.charAt(<i>k</i>) == ch) && (<i>k</i> &lt;= fromIndex)
        </pre></blockquote>
        <blockquote><pre>
          Unfinished blockquote
        </pre> </blockquote>
        </div><table class='sections'></table>
    """.trimIndent()
    TestCase.assertEquals(expected, doc)
  }

  fun testInheritDoc() {
    configure("""
      public interface A {
        /**
         * A::m-doc
         * @param a A::m-param
         */
        void m(int a);
      }
      public interface B extends A {
        /**
         * B::m-doc
         * @param a B::m-param
         */
        void m(int a);
      }
      public interface C extends B {
        /**
         * {@inheritDoc A}
         * @param a {@inheritDoc}
         */
        void m<caret>(int a);
      }
    """.trimIndent())

    val text = retrieveMethodDocString()
    assertContains(text, "A::m-doc")
    assertFails { assertContains(text, "B::m-doc") }
    assertContains(text, "B::m-param")
    assertFails { assertContains(text, "A::m-param") }
  }

  private fun retrieveMethodDocString(): String {
    val method = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiMethod::class.java)
    val doc = JavaDocumentationProvider().generateDoc(method, null)
    assert(doc != null)
    val text = doc.toString()
    return text
  }

  fun testInheritDocRecursive() {
    configure("""
      public interface A {
        /**
         * @param a A::m-param
         */
        void m(int a);
      }
      public interface B extends A {
        /**
         * @param a {@inheritDoc}
         */
        void m(int a);
      }
      public interface C extends B {
        /**
         * @param a {@inheritDoc B}
         */
        void m<caret>(int a);
      }
    """.trimIndent())

    val text = retrieveMethodDocString()
    assertContains(text, "A::m-param")
  }

  fun testInheritDocImplementOrder() {
    data class Param(val order: String, val contains: String, val notContains: String)

    for (param in arrayOf(
      Param("A, B", "A::m-doc", "B::m-doc"),
      Param("B, A", "B::m-doc", "A::m-doc"),
    )) {
      configure("""
      public interface A {
        /** A::m-doc */
        void m(int a);
      }
      public interface B {
        /** B::m-doc */
        void m(int a);
      }
      public interface C extends ${param.order} {
        /** {@inheritDoc} */
        void m<caret>(int a);
      }
    """.trimIndent())

      val text = retrieveMethodDocString()
      assertContains(text, param.contains)
      assertFails { assertContains(text, param.notContains) }
    }
  }

  fun testInheritDocOrder() {
    configure("""
      public interface A {
        /**
         * @param d A::foo-d
         * @param b A::foo-b
         * @param a A::foo-a
         */
        void foo(int a, int b, int c, int d, int e);
      }
      public interface B extends A {
        /**
         * @param d B::foo-d
         * @param b B::foo-b
         */
        void foo(int a, int b, int c, int d, int e);
      }
      public interface C extends B {
        /**
         * @param d C::foo-d
         * @param b C::foo-b
         * @param a C::foo-a
         * @param c C::foo-c
         */
        void foo(int a, int b, int c, int d, int e);
      }
      public class D implements B, C {
        /**
         * @param d D::foo-d
         */
        void foo(int a, int b, int c, int d, int e);
      }
      public interface E {
        /**
         * @param d E::foo-d
         * @param b E::foo-b
         * @param a E::foo-a
         * @param c E::foo-c
         * @param e E::foo-e
         */
        void foo(int a, int b, int c, int d, int e);
      }
      public class F extends D implements E {
        /**
         * @param d {@inheritDoc}
         * @param b {@inheritDoc}
         * @param a {@inheritDoc}
         * @param c {@inheritDoc}
         * @param e {@inheritDoc}
         */
        void foo<caret>(int a, int b, int c, int d, int e);
      }
    """.trimIndent())

    val text = retrieveMethodDocString()
    assertContains(text, "D::foo-d")
    assertContains(text, "B::foo-b")
    assertContains(text, "A::foo-a")
    assertContains(text, "C::foo-c")
    assertContains(text, "E::foo-e")
  }

  fun testInheritDocSkipObject() {
    configure("""
      public interface A {
        /**
         * @param obj A::equals-obj
         */
        public boolean equals(Object obj);
      }
      
      public class B extends A {
        /**
         * @param obj {@inheritDoc}
         * @return {@inheritDoc}
         */
        @Override
        public boolean equals<caret>(Object obj) {
          return super.equals(obj);
        }
      }
    """.trimIndent())

    val text = retrieveMethodDocString()
    // A has priority over Object
    assertContains(text, "A::equals-obj")
    // A has no doc for `return` so we should inherit from Object
    assertContains(text, "if this object is the same as the obj")
  }

  fun testInheritDocThrows() {
    configure("""
      interface A1 {
        /**
         * @throws E1 A1::m-E1
         * @throws E2 A1::m-E2
         * @throws E3 A1::m-E3
         * @throws E4 A1::m-E4
         */
        void m() throws E1, E2, E3, E4;
      }
      
      interface A2 extends A1 {
        /**
         * @throws E1 A2::m-E1
         * @throws E3 A2::m-E3
         * @throws E4 A2::m-E4
         */
        void m() throws E1, E2, E3, E4;
      }
      
      class B implements A2 {
        /**
         * @throws E1 {@inheritDoc}
         * @throws E2 {@inheritDoc}
         * @throws E3 {@inheritDoc A1}
         * @throws E4 {@inheritDoc A2}
         */
        @Override void m<caret>() throws E1, E2, E3, E4 {}
      }
    """)

    val text = retrieveMethodDocString()
    assertContains(text, "A2::m-E1")
    assertContains(text, "A1::m-E2")
    assertContains(text, "A1::m-E3")
    assertContains(text, "A2::m-E4")
  }

  fun testInheritDocParam() {
    configure("""
      interface A1 {
        /**
         * @param p1 A1::m-p1
         * @param p2 A1::m-p2
         * @param p3 A1::m-p3
         * @param p4 A1::m-p4
         */
        void m(int p1, int p2, int p3, int p4);
      }
      
      interface A2 extends A1 {
        /**
         * @param p1 A2::m-p1
         * @param p3 A2::m-p3
         * @param p4 A2::m-p4
         */
        void m(int p1, int p2, int p3, int p4);
      }
      
      class B implements A2 {
        /**
         * @param p1 {@inheritDoc}
         * @param p2 {@inheritDoc}
         * @param p3 {@inheritDoc A1}
         * @param p4 {@inheritDoc A2}
         */
        @Override void m<caret>(int p1, int p2, int p3, int p4) {}
      }
    """)

    val text = retrieveMethodDocString()
    assertContains(text, "A2::m-p1")
    assertContains(text, "A1::m-p2")
    assertContains(text, "A1::m-p3")
    assertContains(text, "A2::m-p4")
  }

  fun testInheritDocTypeParam() {
    configure("""
      interface A1 {
        /**
         * @param <T1> A1::m-T1
         * @param <T2> A1::m-T2
         * @param <T3> A1::m-T3
         * @param <T4> A1::m-T4
         */
        <T1, T2, T3, T4> void m();
      }
      
      interface A2 extends A1 {
        /**
         * @param <T1> A2::m-T1
         * @param <T3> A2::m-T3
         * @param <T4> A2::m-T4
         */
        <T1, T2, T3, T4> void m();
      }
      
      class B implements A2 {
        /**
         * @param <T1> {@inheritDoc}
         * @param <T2> {@inheritDoc}
         * @param <T3> {@inheritDoc A1}
         * @param <T4> {@inheritDoc A2}
         */
        @Override <T1, T2, T3, T4> void m<caret>() {}
      }
    """)

    val text = retrieveMethodDocString()
    assertContains(text, "A2::m-T1")
    assertContains(text, "A1::m-T2")
    assertContains(text, "A1::m-T3")
    assertContains(text, "A2::m-T4")
  }

  fun testInheritDocReturn() {
    configure("""
      interface A1 {
        /** @return A1::m1 */ void m1();
        /** @return A1::m2 */ void m2();
        /** @return A1::m3 */ void m3();
        /** @return A1::m4 */ void m4();
      }
      
      interface A2 extends A1 {
        /** @return A2::m1 */ @Override void m1();
        /** no return tag */ @Override void m2();
        /** @return A2::m3 */ @Override void m3();
        /** @return A2::m4 */ @Override void m4();
      }
      
      class B implements A2 {
        /** @return {@inheritDoc} */ @Override void m1() {}
        /** @return {@inheritDoc} */ @Override void m2() {}
        /** @return {@inheritDoc A1} */ @Override void m3() {}
        /** @return {@inheritDoc A2} */ @Override void m4() {}
      }
    """)

    val expected = mapOf(
      "m1" to "A2::m1",
      "m2" to "A1::m2",
      "m3" to "A1::m3",
      "m4" to "A2::m4",
    )

    for (method in PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)) {
      if (method.containingClass?.name != "B") continue
      val doc = JavaDocumentationProvider().generateDoc(method, null)
      assert(doc != null)
      val text = doc.toString()
      assertContains(text, expected[method.name]!!)
    }
  }

  private fun doTestCtrlHoverDoc(inputFile: String, expectedDoc: String) {
    configure(inputFile.trimIndent())
    val doc = ReadAction.nonBlocking (Callable { CtrlMouseHandler.getGoToDeclarationOrUsagesText (myFixture.editor) }).submit(AppExecutorUtil.getAppExecutorService()).get()
    assertEquals(expectedDoc, UIUtil.getHtmlBody(doc!!))
  }

  fun configure(text: String) {
    myFixture.configureByText("a.java", text)
  }
}