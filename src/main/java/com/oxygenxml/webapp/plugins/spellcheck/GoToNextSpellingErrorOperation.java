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

  @Override
  public String doOperation(AuthorDocumentModel docModel, ArgumentsMap args) 
      throws AuthorOperationException {
    String result = null;
    try {
      SpellcheckContext spellcheckContext = new SpellcheckContext(docModel);

      IgnoredWords ignoredWords = IgnoredWords.fromUncheckedArgument(
          args.getArgumentValue("ignoredWords"), spellcheckContext);
      WebappSpellchecker spellchecker = docModel.getSpellchecker();
      
      Optional<SpellCheckingProblemInfo> maybeNextProblem = 
          findNextProblem(docModel, ignoredWords, spellcheckContext);
      
      if (maybeNextProblem.isPresent()) {
        SpellCheckingProblemInfo nextProblem = maybeNextProblem.get();
        AuthorDocumentController controller = docModel.getAuthorDocumentController();
        // Save informations about the current word
        spellcheckContext.setCurrentWordInfo(SpellcheckWordInfo.from(nextProblem, controller));
        // Select the next spelling error.
        docModel.getSelectionModel().setSelection(nextProblem.getStartOffset(), nextProblem.getEndOffset() + 1);
        String[] suggestions = findSuggestions(spellchecker, nextProblem);
        result = getFindResult(nextProblem, suggestions);
      }

    } catch (BadLocationException e) {
      throw new AuthorOperationException(e.getMessage(), e);
    }
    return result;
  }

  /**
   * Get the result of finding next problem.
   * 
   * @param nextProblem Next problem
   * @param suggestions Suggestion
   * @return
   * @throws AuthorOperationException
   */
  private String getFindResult(SpellCheckingProblemInfo nextProblem, String[] suggestions) throws AuthorOperationException {
    String result = null;
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
    return result;
  }

  /**
   * Save the position of the current spelling error.
   * 
   * @param docModel The document model.
   * rather than the last position marked.
   * @param ignoredWords The ignored words.
   * @param spellcheckContext Spellcheck context.
   * 
   * @return Info about the next spell-checking problem, if any.
   * @throws AuthorOperationException
   */
  private Optional<SpellCheckingProblemInfo> findNextProblem(AuthorDocumentModel docModel, 
      IgnoredWords ignoredWords, SpellcheckContext spellcheckContext) throws AuthorOperationException {
    WebappSpellchecker spellchecker = docModel.getSpellchecker();
    AuthorDocument document = docModel.getAuthorDocumentController().getAuthorDocumentNode();
    
    int startOffset = docModel.getSelectionModel().getCaretOffset();
    SpellcheckWordInfo currentWord = spellcheckContext.getCurrentWord();
    if (currentWord != null) {
      startOffset = currentWord.getEndPosition().getOffset();
    }
    
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
