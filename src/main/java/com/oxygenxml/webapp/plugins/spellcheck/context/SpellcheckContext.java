package com.oxygenxml.webapp.plugins.spellcheck.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.Position;

import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;
import ro.sync.ecss.extensions.api.access.EditingSessionContext;
import ro.sync.ecss.extensions.api.webapp.AuthorDocumentModel;

/**
 * Manager for spellcheck context. 
 * 
 * @author mihaela
 */
public class SpellcheckContext {
  /**
   * Attribute name for the start position of the last spelling error.
   */
  private static final String CURRENT_WORD_INFO = "com.oxygenxml.plugins.spellcheck.currentWord";
  /**
   * Attribute name for the start position when the dialog was open.
   */
  private static final String IGNORED_WORDS_INFO = "com.oxygen.plugins.spellcheck.ignoredWords";
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
   * Editing session context.
   */
  private EditingSessionContext editingContext;
  /**
   * Author document controller.
   */
  private AuthorDocumentController controller;
  
  /**
   * Constructor.
   * 
   * @param model Author document model.
   */
  public SpellcheckContext(AuthorDocumentModel model) {
    this.editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
    this.controller = model.getAuthorDocumentController();
  }
  
  /**
   * Set current word info.
   * 
   * @param wordInfo The word info.
   */
  public void setCurrentWordInfo(SpellcheckWordInfo wordInfo) {
    editingContext.setAttribute(CURRENT_WORD_INFO, wordInfo);
  }
  
  /**
   * Get current word.
   * 
   * @return The current word.
   */
  public SpellcheckWordInfo getCurrentWord() {
    return (SpellcheckWordInfo) editingContext.getAttribute(CURRENT_WORD_INFO);
  }
  
  /**
   * Add current word to ignored words.
   * 
   * @param wordInfo The word info.
   */
  public void ignoreCurrentWord() {
    List<SpellcheckWordInfo> ignoredWords = getIgnoredWords();
    SpellcheckWordInfo currentWord = getCurrentWord();
    
    int index = Collections.binarySearch(ignoredWords, currentWord, ignoredWordsStartPositionsComparator);
    if(index < 0) {      
      int insertionPoint = -index - 1;
      ignoredWords.add(insertionPoint, currentWord);        
    } else {
      ignoredWords.remove(index);
      ignoredWords.add(index, currentWord);
    }
    
    editingContext.setAttribute(IGNORED_WORDS_INFO, ignoredWords);
  }
  
  /**
   * Get ignored words.
   * 
   * @return The ignored words.
   */
  @SuppressWarnings("unchecked")
  public List<SpellcheckWordInfo> getIgnoredWords() {
    List<SpellcheckWordInfo> ignoredWords = new ArrayList<>();

    Object ignoredWordsAttribute = editingContext.getAttribute(IGNORED_WORDS_INFO);
    if (ignoredWordsAttribute instanceof List) {
      ignoredWords = (List<SpellcheckWordInfo>) ignoredWordsAttribute;
    } 
    return ignoredWords;
  }
  
  /**
   * Clear all information saved for the current spellcheck context.
   */
  public void clear() {
    editingContext.setAttribute(CURRENT_WORD_INFO, null);
    editingContext.setAttribute(IGNORED_WORDS_INFO, null);
  }

  /**
   * Find if a specific problem is ignored. 
   * 
   * @param problem The spellcheck problem information
   * @return <code>true</code> if the spellcheck problem is ignored.
   * @throws BadLocationException 
   */
  public boolean isIgnored(SpellCheckingProblemInfo problem) throws BadLocationException {
    boolean ignored = false;
    List<SpellcheckWordInfo> ignoredWords = getIgnoredWords();
    SpellcheckWordInfo wordInfo = SpellcheckWordInfo.from(problem, controller);
    
    int index = Collections.binarySearch(ignoredWords, wordInfo, ignoredWordsStartPositionsComparator);
    if (index >= 0) {
      SpellcheckWordInfo matchingWord = ignoredWords.get(index);
      ignored = matchingWord.equals(wordInfo);
      if (!ignored) {
        // It seems that there is an old spellcheck problem registered as ignored
        // it should be removed 
        ignoredWords.remove(index);
      }
    }
    return ignored;
  }
}
