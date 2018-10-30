package com.oxygenxml.webapp.plugins.spellcheck;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;

/**
 * Object holding the currently ignored words.
 *
 * @author ctalau
 */
public class IgnoredWords {

  /**
   * Ignored words per language.
   */
  private final Map<String, List<?>> ingoredWordsTyped = new HashMap<String, List<?>>();;

  /**
   * Private constructor - use the factory method.
   */
  private IgnoredWords() {
  }
  
  /**
   * Checks if a word is ignored for a particular language.
   * 
   * @param problem The spell checking problem,
   * 
   * @return <code>true</code> if the problem is ignored.
   */
  public boolean isIgnored(SpellCheckingProblemInfo problem) {
    String canonicalLang = getCanonicalLanguage(problem.getLanguageIsoName());
    List<?> ignoredWordsForLang = 
        ingoredWordsTyped.getOrDefault(canonicalLang, Collections.emptyList());
    return ignoredWordsForLang.contains(problem.getWord());
  }
  
  /**
   * Constructs a set of ignored objects from an unchecked argument sent from client-side.
   * 
   * @param ignoredWordsArg The argument.
   * 
   * @return The instance.
   * 
   * @throws IllegalArgumentException if the argument does not have the expected shape.
   */
  public static IgnoredWords fromUncheckedArgument(Object ignoredWordsArg) {
    IgnoredWords ignoredWordsObj = new IgnoredWords();
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
