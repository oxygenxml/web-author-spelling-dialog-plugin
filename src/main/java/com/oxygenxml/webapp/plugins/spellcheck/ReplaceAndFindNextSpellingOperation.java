package com.oxygenxml.webapp.plugins.spellcheck;

import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.Segment;

import org.apache.log4j.Logger;

import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.webapp.AuthorDocumentModel;
import ro.sync.ecss.extensions.api.webapp.AuthorOperationWithResult;
import ro.sync.ecss.extensions.api.webapp.WebappRestSafe;
import ro.sync.ecss.extensions.api.webapp.findreplace.WebappFindOptions;

import com.oxygenxml.webapp.plugins.spellcheck.context.SpellcheckContext;
import com.oxygenxml.webapp.plugins.spellcheck.context.SpellcheckWordInfo;

/**
 * Replace current spelling error and find next problem.
 * 
 * @author mihaela
 */
@WebappRestSafe
public class ReplaceAndFindNextSpellingOperation extends AuthorOperationWithResult {
  /**
   * Logger.
   */
  private Logger logger = Logger.getLogger(ReplaceAndFindNextSpellingOperation.class);
  
  /**
   * Attribute name for the "replace all" option.
   */
  private static final String REPLACE_ALL_ARGUMENT_NAME = "replaceAll";
  
  /**
   * Attribute name for new word (to replace with).
   */
  private static final String NEW_WORD_ARGUMENT_NAME = "newWord";
  
  /**
   * Attribute name for ignored words. Necessary for finding the next spellcheck problem
   */
  public static final String IGNORED_WORDS_ARGUMENT_NAME = "ignoredWords";
  
  /**
   * Word changed response
   */
  private static final String WORD_CHANGED_RESPONSE = "{\"wordChanged\" :true}";
  

  @Override
  public String doOperation(AuthorDocumentModel model, ArgumentsMap args) throws AuthorOperationException {
    String newWord = (String)args.getArgumentValue(NEW_WORD_ARGUMENT_NAME);
    
    SpellcheckContext spellcheckContext = new SpellcheckContext(model);
    SpellcheckWordInfo currentWordInfo = spellcheckContext.getCurrentWord();
    
    if (isReplaceAll(args)) {
      replaceAll(model, currentWordInfo.getWord(), newWord);      
    } else {
      Position startPosition = currentWordInfo.getStartPosition();
      Position endPosition = currentWordInfo.getEndPosition();

      String oldWord = getOldWord(model, startPosition, endPosition);
      if (!currentWordInfo.getWord().equals(oldWord)) {
        return WORD_CHANGED_RESPONSE;
      }
      
      replace(model, newWord, startPosition, endPosition);
    } 

    return new GoToNextSpellingErrorOperation().doOperation(model, args);
  }

  /**
   * Get the old word.
   * 
   * @param model The Author document model.
   * @param startPosition The word start position.
   * @param endPosition The word end position.
   * @return
   * @throws AuthorOperationException 
   */
  private String getOldWord(AuthorDocumentModel model, Position startPosition,
      Position endPosition) throws AuthorOperationException {
    String oldWord = null;
    try {
      Segment chars = new Segment();
      model.getAuthorDocumentController().getChars(startPosition.getOffset(), 
          endPosition.getOffset() - startPosition.getOffset() + 1, chars);
      oldWord = chars.toString();
    } catch (BadLocationException e) {
      logger.error(e);
    }
    return oldWord;
  }

  /**
   * Check if a replace all must be performed.
   * 
   * @param args The operation arguments.
   * @return <code>true</code> if replace all must be performed (<code>false</code> for a single 
   * occurrence replace).
   */
  private boolean isReplaceAll(ArgumentsMap args) {
    boolean replaceAll = false;
    Object replaceAllArg = args.getArgumentValue(REPLACE_ALL_ARGUMENT_NAME);
    if (replaceAllArg instanceof Boolean) {
      replaceAll = (Boolean)replaceAllArg;
    }
    return replaceAll;
  }

  /**
   * Replace word.
   * 
   * @param model Author document model.
   * @param newWord Word to replace with.
   * @param startPosition The start position of the last spelling error.
   * @param endPosition The end position of the last spelling error.
   */
  private void replace(AuthorDocumentModel model, String newWord, 
      Position startPosition, Position endPosition) {
    int startOffset = startPosition.getOffset();
    int endOffset = endPosition.getOffset();

    model.getAuthorDocumentController().delete(startOffset, endOffset);
    model.getAuthorDocumentController().insertText(startOffset, newWord);
    model.getSelectionModel().moveTo(startOffset + newWord.length());
  }

  /**
   * Replace all occurrences of a word.
   * 
   * @param model Author document model.
   * @param oldWord Word to be replaced.
   * @param newWord Word to replace with.
   * 
   * @throws AuthorOperationException
   */
  private void replaceAll(AuthorDocumentModel model, String oldWord, String newWord) {
    WebappFindOptions options = new WebappFindOptions();
    options.setMatchCase(true);
    options.setWholeWords(true);
    model.getFindReplaceSupport().replaceAll(oldWord, newWord, options);
  }
}
