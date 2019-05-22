package com.oxygenxml.webapp.plugins.spellcheck;
        
import java.io.IOException;
import java.util.Optional;

import javax.swing.text.BadLocationException;
import javax.swing.text.Position;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import com.google.common.collect.ImmutableMap;

import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;
import ro.sync.ecss.extensions.api.SpellSuggestionsInfo;
import ro.sync.ecss.extensions.api.access.EditingSessionContext;
import ro.sync.ecss.extensions.api.node.AuthorDocument;
import ro.sync.ecss.extensions.api.webapp.AuthorDocumentModel;
import ro.sync.ecss.extensions.api.webapp.AuthorOperationWithResult;
import ro.sync.ecss.extensions.api.webapp.WebappRestSafe;
import ro.sync.ecss.extensions.api.webapp.WebappSpellchecker;

/**
 * Operation that goes to the next spelling error.
 * 
 * @author ctalau
 */
@WebappRestSafe
public class GoToNextSpellingErrorOperation extends AuthorOperationWithResult {
  /**
   * Logger.
   */
  Logger logger = Logger.getLogger(GoToNextSpellingErrorOperation.class);
  /**
   * Attribute name for the start position of the last spelling error.
   */
  private static final String START_POS_ATTR_NAME = "com.oxygenxml.plugins.spellcheck.startPos";
  /**
   * Attribute name for the end position of the last spelling error.
   */
  private static final String END_POS_ATTR_NAME = "com.oxygenxml.plugins.spellcheck.endPos";
  /**
   * Attribute name for the start position when the dialog was open.
   */
  private static final String MAN_SP_START = "com.oxygen.plugins.spellcheck.spellcheckStartPos";
  /**
   * Save wrapped status.
   */
  private static final String MAN_SP_WRAPPED = "com.oxygen.plugins.spellcheck.wrapped";

  /**
   * Save last checked word.
   */
  private static final String MAN_SP_WORD = "com.oxygen.plugins.spellcheck.word";
  
  /**
   * Save language for last checked word.
   */
  private static final String MAN_SP_LANG = "com.oxygen.plugins.spellcheck.lang";

  @Override
  public String doOperation(AuthorDocumentModel docModel, ArgumentsMap args)
      throws AuthorOperationException {
    String result = null;
    
    boolean saveStartPosition = false;
    Object saveStartArg = args.getArgumentValue("saveStartPosition");
    if (saveStartArg instanceof Boolean) {
      saveStartPosition = (boolean) saveStartArg;
    }
    
    IgnoredWords ignoredWords = 
        IgnoredWords.fromUncheckedArgument(args.getArgumentValue("ignoredWords"));
    WebappSpellchecker spellchecker = docModel.getSpellchecker();
    
    Optional<SpellCheckingProblemInfo> maybeNextProblem = 
        findNextProblem(docModel, true, ignoredWords);
    
    if (maybeNextProblem.isPresent()) {
      boolean wrapped = false;
      
      SpellCheckingProblemInfo nextProblem = maybeNextProblem.get();
      if (saveStartPosition) {
        saveSpellcheckStartPosition(docModel, nextProblem.getStartOffset());
        // If first search it could not have wrapped.
        // This covers the case when the first error comes after a wrap.
        saveWrappedStatus(docModel, false);
      } else {
        // This is not the first search.
        SpellCheckingProblemInfo previousProblem = getPreviousProblem(docModel);
        if (isProbablySameProblem(previousProblem, nextProblem) || 
            previousProblem.getStartOffset() > nextProblem.getStartOffset()) {
          saveWrappedStatus(docModel, true);
        }
        
        wrapped = getWrappedStatus(docModel);
        int spellcheckStartPosition = getSpellcheckStartPosition(docModel);
        if (wrapped && spellcheckStartPosition != -1 && nextProblem.getStartOffset() >= spellcheckStartPosition) {
          return result;
        }
      }
      
      saveCurrentProblem(docModel, nextProblem);
      
      // Select the next spelling error.
      docModel.getSelectionModel().setSelection(
          nextProblem.getStartOffset(), nextProblem.getEndOffset() + 1);
      
      String[] suggestions = findSuggestions(spellchecker, nextProblem);
      
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        result = objectMapper.writeValueAsString(ImmutableMap.of(
            "word", nextProblem.getWord(),
            "language", nextProblem.getLanguageIsoName(),
            "startOffset", nextProblem.getStartOffset(),
            "endOffset", nextProblem.getEndOffset(),
            "suggestions", suggestions));
      } catch (IOException e) {
        throw new AuthorOperationException(e.getMessage(), e);
      }
    }
    return result;
  }

  /**
   * Get the wrapped status of the current session of manual spell check.
   * @param docModel The author document model.
   * @return Whether the search wrapped in this session.
   */
  private boolean getWrappedStatus(AuthorDocumentModel docModel) {
    EditingSessionContext editingContext = docModel.getAuthorAccess().getEditorAccess().getEditingContext();
    boolean wrappedStatus = false;
    Object wrappedStatusObject = editingContext.getAttribute(MAN_SP_WRAPPED);
    if (wrappedStatusObject != null) {
      wrappedStatus = (boolean) wrappedStatusObject;
    }
    return wrappedStatus;
  }

  /**
   * Check if two problems are likely to be the same one.
   * @param previousProblem The previous problem.
   * @param nextProblem The next problem.
   * @return Whether they are likely to be the same problem.
   */
  private boolean isProbablySameProblem(SpellCheckingProblemInfo previousProblem, 
      SpellCheckingProblemInfo nextProblem) {
    return previousProblem.getStartOffset() == nextProblem.getStartOffset() &&
        previousProblem.getEndOffset() == nextProblem.getEndOffset() &&
        previousProblem.getWord().equals(nextProblem.getWord()) &&
        previousProblem.getLanguageIsoName().equals(nextProblem.getLanguageIsoName());
  }

  /**
   * Save the position of the first error found in this session of manual spell check.
   * @param docModel The author document model.
   * @param spellcheckStartPos The start position of the first error.
   * @throws AuthorOperationException
   */
  private void saveSpellcheckStartPosition(AuthorDocumentModel docModel, int spellcheckStartPos) throws AuthorOperationException {
    EditingSessionContext editingContext = docModel.getAuthorAccess().getEditorAccess().getEditingContext();
    try {
      AuthorDocumentController controller = docModel.getAuthorDocumentController();
      editingContext.setAttribute(MAN_SP_START, controller.createPositionInContent(spellcheckStartPos));
    } catch (BadLocationException e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
  }
  
  /**
   * Remember if the search wraps.
   * @param docModel The author document model.
   * @param wrapped The wrap status.
   */
  private void saveWrappedStatus(AuthorDocumentModel docModel, boolean wrapped) {
    EditingSessionContext editingContext = docModel.getAuthorAccess().getEditorAccess().getEditingContext();
    editingContext.setAttribute(MAN_SP_WRAPPED, wrapped);
  }
  
  /**
   * Return the position where the manual spell check started. -1 if not set.
   * @param docModel The document model.
   * @return The position where the manual spell check started or -1 if not set.
   */
  private int getSpellcheckStartPosition(AuthorDocumentModel docModel) {    
    EditingSessionContext editingContext = docModel.getAuthorAccess().getEditorAccess().getEditingContext();
    Integer spellcheckStartOffset = -1;
    Position spellcheckStartPosition = (Position)editingContext.getAttribute(MAN_SP_START);
    if (spellcheckStartPosition != null) {
      spellcheckStartOffset = spellcheckStartPosition.getOffset();
    }
    return spellcheckStartOffset;
  }

  /**
   * Save the position of the current spelling error.
   * 
   * @param docModel The document model.
   * @param fromCaret <code>true</code> to return the next problem from the caret position
   * rather than the last position marked.
   * @param ignoredWords The ignored words.
   * 
   * @return Info about the next spell-checking problem, if any.
   * @throws AuthorOperationException
   */
  private Optional<SpellCheckingProblemInfo> findNextProblem(AuthorDocumentModel docModel, boolean fromCaret, IgnoredWords ignoredWords)
      throws AuthorOperationException {
    WebappSpellchecker spellchecker = docModel.getSpellchecker();
    AuthorDocument document = docModel.getAuthorDocumentController().getAuthorDocumentNode();
    
    int startOffset = getStartOffset(docModel, fromCaret);
    
    SpellcheckPerformer spellcheckPerformer = new SpellcheckPerformer(
        spellchecker, 
        ignoredWords,
        document.getEndOffset());
    
    Optional<SpellCheckingProblemInfo> problemInfo = 
        spellcheckPerformer.runSpellcheck(startOffset, document.getEndOffset());
    if (!problemInfo.isPresent()) {
      problemInfo = spellcheckPerformer.runSpellcheck(0, startOffset);
    }
    return problemInfo;
  }

  /**
   * Return the offset from which we should start the spellchecking.
   * 
   * @param docModel the document model.
   * @param fromCaret <code>true</code> if we should start from the carer pos.
   * 
   * @return The start offset.
   */
  private int getStartOffset(AuthorDocumentModel docModel, boolean fromCaret) {
    if (fromCaret) {
      return docModel.getSelectionModel().getCaretOffset();
    } else {
      EditingSessionContext editingContext = docModel.getAuthorAccess().getEditorAccess().getEditingContext();
      Integer lastEnd = (Integer) editingContext.getAttribute(END_POS_ATTR_NAME);
      return lastEnd != null ? lastEnd : 0;
    }
  }

  /**
   * Saves the current spelling problem so that we can resume from it the next time.
   * 
   * @param model The document model.
   * @param currentProblem The current problem.
   * 
   * @throws AuthorOperationException
   */
  private void saveCurrentProblem(AuthorDocumentModel model, SpellCheckingProblemInfo currentProblem)
      throws AuthorOperationException {
    EditingSessionContext editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
    try {
      AuthorDocumentController controller = model.getAuthorDocumentController();
      editingContext.setAttribute(START_POS_ATTR_NAME, 
          controller.createPositionInContent(currentProblem.getStartOffset()));
      editingContext.setAttribute(END_POS_ATTR_NAME, 
          controller.createPositionInContent(currentProblem.getEndOffset()));
      
      // Also save the word and language to check for the case where
      // there is only one error and the user hits ignore.
      editingContext.setAttribute(MAN_SP_WORD, currentProblem.getWord());
      editingContext.setAttribute(MAN_SP_LANG, currentProblem.getLanguageIsoName());
    } catch (BadLocationException e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
  }
  
  /**
   * Get some information about the previous problem.
   * It is not a complete problem info object.
   * @param model The author document model.
   * @return The problem info.
   */
  private SpellCheckingProblemInfo getPreviousProblem(AuthorDocumentModel model) {
    EditingSessionContext editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
    Position startPos = getStartPositionOfLastError(model);
    Position endPos = getEndPositionOfLastError(model);
    String word = (String) editingContext.getAttribute(MAN_SP_WORD);
    String lang = (String) editingContext.getAttribute(MAN_SP_LANG);
    return new SpellCheckingProblemInfo(startPos.getOffset(), endPos.getOffset(), 0, lang, word);
  }

  /**
   * Get the end position of the last spelling error
   * 
   * @param model Author document model 
   * @return the end position.
   */
  public Position getEndPositionOfLastError(AuthorDocumentModel model) {
    EditingSessionContext editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
    return (Position) editingContext.getAttribute(END_POS_ATTR_NAME);
  }

  /**
   * Get the start position of the last spelling error.
   * 
   * @param model Author document model.
   * @return the start position.
   */
  public Position getStartPositionOfLastError(AuthorDocumentModel model) {
    EditingSessionContext editingContext = model.getAuthorAccess().getEditorAccess().getEditingContext();
    return (Position) editingContext.getAttribute(START_POS_ATTR_NAME);
  }

  /**
   * Finds suggestions for the current spelling problem.
   * 
   * @param spellchecker The spellchecker.
   * @param nextProblem The spelling problem.
   * 
   * @return The list of suggestions.
   * 
   * @throws AuthorOperationException
   */
  private String[] findSuggestions(WebappSpellchecker spellchecker, SpellCheckingProblemInfo nextProblem)
      throws AuthorOperationException {
    String[] suggestions;
    // Custom spell checker may provide suggestions with the problem info.
    if (nextProblem.getSuggestions() != null) {
      suggestions = nextProblem.getSuggestions().toArray(new String[0]);
    } else {
    try {
      SpellSuggestionsInfo suggestionInfo = 
          spellchecker.getSuggestionsForWordAtPosition(nextProblem.getStartOffset() + 1);
      suggestions = suggestionInfo.getSuggestions();
    } catch (Exception e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
    }
    return suggestions;
  }
}
