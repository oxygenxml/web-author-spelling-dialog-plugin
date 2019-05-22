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
   * Attribute name for the old word (to be replaced).
   */
  private static final String OLD_WORD_ARGUMENT_NAME = "oldWord";
  
  /**
   * Attribute name for new word (to replace with).
   */
  private static final String NEW_WORD_ARGUMENT_NAME = "newWord";
  
  /**
   * Attribute name for ignored words. Necessary for finding the next spellcheck problem
   */
  public static final String IGNORED_WORDS_ARGUMENT_NAME = "ignoredWords";
  

  @Override
  public String doOperation(AuthorDocumentModel model, ArgumentsMap args) throws AuthorOperationException {
    GoToNextSpellingErrorOperation findNextOperation = new GoToNextSpellingErrorOperation();
    
    String oldWord = (String)args.getArgumentValue(OLD_WORD_ARGUMENT_NAME);
    String newWord = (String)args.getArgumentValue(NEW_WORD_ARGUMENT_NAME);
    
    if (isReplaceAll(args)) {
      replaceAll(model, oldWord, newWord);      
    } else {
      Position startPosition = findNextOperation.getStartPositionOfLastError(model);
      Position endPosition = findNextOperation.getEndPositionOfLastError(model);
      
      replace(model, oldWord, newWord, startPosition, endPosition);
    } 

    return findNextOperation.doOperation(model, args);
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
   * @param oldWord Word to be replaced.
   * @param newWord Word to replace with.
   * @param startPosition The start position of the last spelling error.
   * @param endPosition The end position of the last spelling error.
   * 
   * @throws AuthorOperationException
   */
  private void replace(AuthorDocumentModel model, String oldWord, String newWord, 
      Position startPosition, Position endPosition) 
      throws AuthorOperationException {
    int startOffset = startPosition.getOffset();
    int endOffset = endPosition.getOffset();

    Segment chars = new Segment();
    try {
      model.getAuthorDocumentController().getChars(startOffset, endOffset - startOffset + 1, chars);
      // Check if word was not modified.
      if (oldWord.equals(chars.toString())){
        model.getAuthorDocumentController().delete(startOffset, endOffset);
        model.getAuthorDocumentController().insertText(startOffset, newWord);
        model.getSelectionModel().moveTo(startOffset + newWord.length());
      } else {
        throw new AuthorOperationException("Document changed since replace operation started.");
      }
    } catch (BadLocationException e) {
      logger.error(e);
      throw new AuthorOperationException("Replace operation failed: " + e.getMessage());
    }
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
