package com.oxygenxml.webapp.plugins.spellcheck.context;

import java.util.Objects;

import javax.swing.text.BadLocationException;
import javax.swing.text.Position;

import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;

/**
 * Information for a spellcheck word error.
 */
public class SpellcheckWordInfo {

  /**
   * Start position of the word.
   */
  private Position startPosition;
  
  /**
   * End position of the word.
   */
  private Position endPosition;
  
  /**
   * Language name.
   */
  private String languageIsoName;
  
  /**
   * The word.
   */
  private String word;
  
  /**
   * Set the start position.
   * 
   * @param startPosition The start position.
   */
  public void setStartPosition(Position startPosition) {
    this.startPosition = startPosition;
  }
  
  /**
   * @return Returns the start position.
   */
  public Position getStartPosition() {
    return startPosition;
  }  
  
  /**
   * Set the end position.
   * 
   * @param endPosition The end position.
   */
  public void setEndPosition(Position endPosition) {
    this.endPosition = endPosition;
  }
  
  /**
   * @return Returns the end position.
   */
  public Position getEndPosition() {
    return endPosition;
  }
  
  /**
   * Set the language name.
   * 
   * @param languageIsoName The language name.
   */
  public void setLanguageIsoName(String languageIsoName) {
    this.languageIsoName = languageIsoName;
  }
  
  /**
   * @return Returns the language name.
   */
  public String getLanguageIsoName() {
    return languageIsoName;
  }
  
  /**
   * Set the word.
   * 
   * @param word The word.
   */
  public void setWord(String word) {
    this.word = word;
  }
  
  /**
   * @return Returns the word. 
   */
  public String getWord() {
    return word;
  }

  /**
   * Create a {@link SpellcheckWordInfo} from a {@link SpellCheckingProblemInfo}
   * 
   * @param problem The problem.
   * @param controller The controller.
   * @return The word info.
   * @throws BadLocationException 
   */
  public static SpellcheckWordInfo from(SpellCheckingProblemInfo problem, AuthorDocumentController controller) 
      throws BadLocationException {
    SpellcheckWordInfo wordInfo = new SpellcheckWordInfo();
    wordInfo.setStartPosition(controller.createPositionInContent(problem.getStartOffset()));
    wordInfo.setEndPosition(controller.createPositionInContent(problem.getEndOffset()));
    wordInfo.setWord(problem.getWord());
    wordInfo.setLanguageIsoName(problem.getLanguageIsoName());
    
    return wordInfo;
  }
 
  @Override
  public boolean equals(Object obj) {
    boolean equals = false;
    if (obj instanceof SpellcheckWordInfo) {
      SpellcheckWordInfo wordInfo = (SpellcheckWordInfo) obj;
      equals = wordInfo.getStartPosition().getOffset() == this.getStartPosition().getOffset() && 
          wordInfo.getEndPosition().getOffset() == this.getEndPosition().getOffset() &&
          wordInfo.getWord().equals(this.getWord()) && 
          wordInfo.getLanguageIsoName().equals(this.getLanguageIsoName());
    } 
    return equals;
  }
  
  @Override
  public int hashCode() {
      return Objects.hash(word, languageIsoName, startPosition, endPosition);
  }
}
