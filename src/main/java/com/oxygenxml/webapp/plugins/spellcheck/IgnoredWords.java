package com.oxygenxml.webapp.plugins.spellcheck;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.BadLocationException;

import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;

import com.oxygenxml.webapp.plugins.spellcheck.context.SpellcheckContext;

/**
 * Object holding the currently ignored words.
 *
 * @author ctalau
 */
public class IgnoredWords {

  /**
   * Ignored words per language.
   */
  private final Map<String, List<?>> ingoredWordsTyped = new HashMap<>();
  /**
   * The spellcheck context.
   */
  private SpellcheckContext spellcheckContext;

  /**
   * Private constructor - use the factory method.
   * @param spellcheckContext The spellcheck context.
   */
  private IgnoredWords(SpellcheckContext spellcheckContext) {
    this.spellcheckContext = spellcheckContext;
  }
  
  /**
   * Checks if a word is ignored for a particular language.
   * 
   * @param problem The spell checking problem,
   * @param controller Author document controller
   * 
   * @return <code>true</code> if the problem is ignored.
   * @throws BadLocationException 
   */
  public boolean isIgnored(SpellCheckingProblemInfo problem, AuthorDocumentController controller) throws BadLocationException {
    String canonicalLang = getCanonicalLanguage(problem.getLanguageIsoName());
    List<?> ignoredWordsForLang = 
        ingoredWordsTyped.getOrDefault(canonicalLang, Collections.emptyList());
    return ignoredWordsForLang.contains(problem.getWord()) || spellcheckContext.isIgnored(problem, controller);
  }
  
  /**
   * Constructs a set of ignored objects from an unchecked argument sent from client-side.
   * 
   * @param ignoredWordsArg The argument.
   * @param spellcheckContext The spellcheck context. 
   * 
   * @return The instance.
   * 
   * @throws IllegalArgumentException if the argument does not have the expected shape.
   */
  public static IgnoredWords fromUncheckedArgument(Object ignoredWordsArg, 
      SpellcheckContext spellcheckContext) {
    IgnoredWords ignoredWordsObj = new IgnoredWords(spellcheckContext);
    try {
      Map<?, ?> ignoredWordsUntyped = (Map<?, ?>) ignoredWordsArg;
      for (Map.Entry<?, ?> entry: ignoredWordsUntyped.entrySet()) {
        String lang = (String)entry.getKey();
        String canonicalLang = getCanonicalLanguage(lang);
        ignoredWordsObj.ingoredWordsTyped.put(canonicalLang, (List<?>)entry.getValue());
      }
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("ingoredWords", e);
    }
    
    return ignoredWordsObj;
  }

  /**
   * Canonicalize the language.
   * 
   * @param lang The input language, e.g. 'en_US'.
   * 
   * @return The canonical language, e.g. 'en'.
   */
  private static String getCanonicalLanguage(String lang) {
    return lang.substring(0, 2);
  }
}
