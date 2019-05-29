package com.oxygenxml.webapp.plugins.spellcheck.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.Position;

import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;

/**
 * The spellcheck context. 
 * 
 * @author mihaela
 */
public class SpellcheckContext {
  /**
   * Attribute name for spellcheck context (that is used to be saved in the editing context)
   */
  public static final String SPELLCHECK_CONTEXT_ATTR_NAME = "com.oxygenxml.plugins.spellcheck.context";
  /**
   * Information for current spellcheck word error.
   */
  private SpellcheckWordInfo currentWordInfo;
  /**
   * list of ignored words
   */
  private List<SpellcheckWordInfo> ignoredWords = new ArrayList<>();
  
  /**
   * Positions comparator for ignored words.
   */
  private static Comparator<SpellcheckWordInfo> ignoredWordsStartPositionsComparator = (SpellcheckWordInfo w1, SpellcheckWordInfo w2) -> {
    int cResult = 0;
    Position w1StartPos = w1.getStartPosition();
    Position w2StartPos = w2.getStartPosition();

    if (!w1StartPos.equals(w2StartPos)) {
      cResult = w1StartPos.getOffset() - w2StartPos.getOffset();
    }
    return cResult;
  };
  
  /**
   * Set current word info.
   * 
   * @param wordInfo The word info.
   */
  public void setCurrentWordInfo(SpellcheckWordInfo wordInfo) {
    this.currentWordInfo = wordInfo;
  }
  
  /**
   * Get current word.
   * 
   * @return The current word.
   */
  public SpellcheckWordInfo getCurrentWord() {
    return this.currentWordInfo;
  }
  
  /**
   * Add current word to ignored words.
   * 
   * @param wordInfo The word info.
   */
  public void ignoreCurrentWord() {
    SpellcheckWordInfo currentWord = getCurrentWord();
    
    int index = Collections.binarySearch(this.ignoredWords, currentWord, ignoredWordsStartPositionsComparator);
    if(index < 0) {      
      int insertionPoint = -index - 1;
      this.ignoredWords.add(insertionPoint, currentWord);        
    } else {
      this.ignoredWords.remove(index);
      this.ignoredWords.add(index, currentWord);
    }
  }
  
  /**
   * Get ignored words.
   * 
   * @return The ignored words.
   */
  public List<SpellcheckWordInfo> getIgnoredWords() {
    return this.ignoredWords;
  }
  
  /**
   * Find if a specific problem is ignored. 
   * 
   * @param problem The spellcheck problem information
   * @param controller The controller.
   * @return <code>true</code> if the spellcheck problem is ignored.
   * @throws BadLocationException 
   */
  public boolean isIgnored(SpellCheckingProblemInfo problem, AuthorDocumentController controller) throws BadLocationException {
    boolean ignored = false;
    if (!this.ignoredWords.isEmpty()) {
      SpellcheckWordInfo wordInfo = SpellcheckWordInfo.from(problem, controller);

      int index = Collections.binarySearch(this.ignoredWords, wordInfo, ignoredWordsStartPositionsComparator);
      if (index >= 0) {
        SpellcheckWordInfo matchingWord = this.ignoredWords.get(index);
        ignored = matchingWord.equals(wordInfo);
        if (!ignored) {
          // It seems that there is an old spellcheck problem registered as ignored
          // it should be removed 
          this.ignoredWords.remove(index);
        }
      }
    }
    return ignored;
  }
}
