package com.oxygenxml.webapp.plugins.spellcheck.context;

import java.util.ArrayList;
import java.util.List;

import javax.swing.text.Position;

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
  private static final String SPELLCHECK_START_POSITION = "com.oxygen.plugins.spellcheck.spellcheckStartPos";
  /**
   * Attribute name for the start position when the dialog was open.
   */
  private static final String IGNORED_WORDS_INFO = "com.oxygen.plugins.spellcheck.ignoredWords";
  /**
   * Save wrapped status.
   */
  private static final String WRAPPED_STATUS = "com.oxygen.plugins.spellcheck.wrapped";
  
  /**
   * Editing session context.
   */
  private EditingSessionContext editingContext;
  
  /**
   * Constructor.
   * 
   * @param model Author document model.
   */
  public SpellcheckContext(AuthorDocumentModel model) {
    this.editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
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
   * Set the start position of the spellcheck.
   * 
   * @param position Start position for spellcheck.
   */
  public void setSpellcheckStartPosition(Position position) {
    editingContext.setAttribute(SPELLCHECK_START_POSITION, position);
  }
  
  /**
   * Return the position where the manual spell check started. -1 if not set.
   * 
   * @param docModel The document model.
   * @return The position where the manual spell check started or -1 if not set.
   */
  public int getSpellcheckStartOffset() {    
    Integer spellcheckStartOffset = -1;
    Position spellcheckStartPosition = (Position) editingContext.getAttribute(SPELLCHECK_START_POSITION);
    if (spellcheckStartPosition != null) {
      spellcheckStartOffset = spellcheckStartPosition.getOffset();
    }
    return spellcheckStartOffset;
  }
  
  /**
   * Add current word to ignored words.
   * 
   * @param wordInfo The word info.
   */
  public void ignoreCurrentWord() {
    List<SpellcheckWordInfo> ignoredWords = getIgnoredWords();
    ignoredWords.add(getCurrentWord());

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
   * Get the wrapped status of the current manual spell check session.
   * 
   * @return whether the search wrapped in this session.
   */
  public boolean getWrappedStatus() {
    boolean wrappedStatus = false;
    Object wrappedStatusObject = editingContext.getAttribute(WRAPPED_STATUS);
    if (wrappedStatusObject != null) {
      wrappedStatus = (boolean) wrappedStatusObject;
    }
    return wrappedStatus;
  }
  
  /**
   * Remember if the search wraps.
   * 
   * @param wrapped The wrap status.
   */
  public void setWrappedStatus(boolean wrapped) {
    editingContext.setAttribute(WRAPPED_STATUS, wrapped);
  }
  
  /**
   * Clear all information saved for the current spellcheck context.
   */
  public void clear() {
    editingContext.setAttribute(CURRENT_WORD_INFO, null);
    editingContext.setAttribute(SPELLCHECK_START_POSITION, null);
    editingContext.setAttribute(IGNORED_WORDS_INFO, null);
    editingContext.setAttribute(WRAPPED_STATUS, null);
  }
}
