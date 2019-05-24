package com.oxygenxml.webapp.plugins.spellcheck.context;

import javax.swing.text.Position;

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
}
