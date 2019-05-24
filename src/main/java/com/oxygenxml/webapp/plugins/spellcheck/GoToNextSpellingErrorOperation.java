package com.oxygenxml.webapp.plugins.spellcheck;
        
import java.io.IOException;
import java.util.Optional;

import javax.swing.text.BadLocationException;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import ro.sync.ecss.extensions.api.ArgumentsMap;
import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;
import ro.sync.ecss.extensions.api.SpellSuggestionsInfo;
import ro.sync.ecss.extensions.api.node.AuthorDocument;
import ro.sync.ecss.extensions.api.webapp.AuthorDocumentModel;
import ro.sync.ecss.extensions.api.webapp.AuthorOperationWithResult;
import ro.sync.ecss.extensions.api.webapp.WebappRestSafe;
import ro.sync.ecss.extensions.api.webapp.WebappSpellchecker;

import com.google.common.collect.ImmutableMap;
import com.oxygenxml.webapp.plugins.spellcheck.context.SpellcheckContext;
import com.oxygenxml.webapp.plugins.spellcheck.context.SpellcheckWordInfo;

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
   * Spellcheck context.
   */
  private SpellcheckContext spellcheckContext;

  @Override
  public String doOperation(AuthorDocumentModel docModel, ArgumentsMap args)
      throws AuthorOperationException {
    String result = null;
    
    this.spellcheckContext = new SpellcheckContext(docModel);
    
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
        saveSpellcheckStartPosition(nextProblem.getStartOffset(), docModel.getAuthorDocumentController());
        // If first search it could not have wrapped.
        // This covers the case when the first error comes after a wrap.
        spellcheckContext.setWrappedStatus(false);
      } else {
        // This is not the first search.
        SpellCheckingProblemInfo previousProblem = getPreviousProblem();
        if (isProbablySameProblem(previousProblem, nextProblem) || 
            previousProblem.getStartOffset() > nextProblem.getStartOffset()) {
          spellcheckContext.setWrappedStatus(true);
        }
        
        wrapped = spellcheckContext.getWrappedStatus();
        int spellcheckStartPosition = spellcheckContext.getSpellcheckStartOffset();
        if (wrapped && spellcheckStartPosition != -1 && nextProblem.getStartOffset() >= spellcheckStartPosition) {
          return result;
        }
      }
      
      saveCurrentProblem(nextProblem, docModel.getAuthorDocumentController());
      
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
   * 
   * @param spellcheckStartPos The start position of the first error.
   * @throws AuthorOperationException
   */
  private void saveSpellcheckStartPosition(int spellcheckStartPos, AuthorDocumentController controller) throws AuthorOperationException {
    try {
      spellcheckContext.setSpellcheckStartPosition(controller.createPositionInContent(spellcheckStartPos));
    } catch (BadLocationException e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
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
      return spellcheckContext.getSpellcheckStartOffset();
    }
  }

  /**
   * Saves the current spelling problem so that we can resume from it the next time.
   * 
   * @param currentProblem The current problem.
   * @param controller The Author document controller
   * 
   * @throws AuthorOperationException
   */
  private void saveCurrentProblem(SpellCheckingProblemInfo currentProblem, AuthorDocumentController controller)
      throws AuthorOperationException {
    try {
      SpellcheckWordInfo wordInfo = new SpellcheckWordInfo();
      wordInfo.setStartPosition(controller.createPositionInContent(currentProblem.getStartOffset()));
      wordInfo.setEndPosition(controller.createPositionInContent(currentProblem.getEndOffset()));
      wordInfo.setWord(currentProblem.getWord());
      wordInfo.setLanguageIsoName(currentProblem.getLanguageIsoName());
      
      spellcheckContext.setCurrentWordInfo(wordInfo);
    } catch (BadLocationException e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
  }
  
  /**
   * Get some information about the previous problem.
   * It is not a complete problem info object.
   * 
   * @return The problem info.
   */
  private SpellCheckingProblemInfo getPreviousProblem() {
    SpellcheckWordInfo currentWordInfo = spellcheckContext.getCurrentWord();
    
    return new SpellCheckingProblemInfo(
        currentWordInfo.getStartPosition().getOffset(), 
        currentWordInfo.getEndPosition().getOffset(),
        0, 
        currentWordInfo.getLanguageIsoName(), 
        currentWordInfo.getWord());
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
