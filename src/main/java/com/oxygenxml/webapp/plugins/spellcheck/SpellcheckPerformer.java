package com.oxygenxml.webapp.plugins.spellcheck;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.swing.text.BadLocationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ro.sync.ecss.extensions.api.AuthorDocumentController;
import ro.sync.ecss.extensions.api.AuthorOperationException;
import ro.sync.ecss.extensions.api.SpellCheckingProblemInfo;
import ro.sync.ecss.extensions.api.webapp.WebappSpellchecker;
import ro.sync.exml.workspace.api.util.TextChunkDescriptor;

import com.google.common.base.MoreObjects;

/**
 * Class used to spellcheck an interval.
 * 
 * @author ctalau
 */
public class SpellcheckPerformer {
  /**
   * Logger.
   */
  Logger logger = LogManager.getLogger(SpellcheckPerformer.class);
  
  /**
   * Spellchecker.
   */
  private WebappSpellchecker spellchecker;
  
  /**
   * The length of the document.
   */
  private int docLength;

  /**
   * The ignored words.
   */
  private IgnoredWords ignoredWords;

  /**
   * Constructor.
   * 
   * @param spellchecker The spellchecker.
   * @param ignoredWords The ignored words.
   * @param docLength The length of the document.
   */
  public SpellcheckPerformer(WebappSpellchecker spellchecker, 
      IgnoredWords ignoredWords, int docLength) {
    this.spellchecker = spellchecker;
    this.ignoredWords = ignoredWords;
    this.docLength = docLength;
  }

  /**
   * Run spellcheck between two offsets.
   * 
   * @param startOffset The start offset.
   * @param endOffset The end offset.
   * @param controller Author document controller
   * 
   * @return The first problem found, or null if everything is ok.
   * 
   * @throws AuthorOperationException If the spellcheck fails.
   */
  public Optional<SpellCheckingProblemInfo> runSpellcheck(
      int startOffset, int endOffset, AuthorDocumentController controller) throws AuthorOperationException {
    logger.debug("Checking between " + startOffset + " " + endOffset);
    int interval = 1000;
    
    int currentOffset = startOffset;
    while (currentOffset < endOffset) {
      int intervalEnd = Math.min(currentOffset + interval, endOffset);
      Optional<SpellCheckingProblemInfo> nextProblem = 
          runSpellcheckSingleInterval(currentOffset, intervalEnd, controller);
      if (nextProblem.isPresent()) {
        logger.debug("Found: " + nextProblem.get().getWord());
        return nextProblem;
      }
      currentOffset = Math.min(currentOffset + interval, endOffset);
    }
    
    return Optional.empty();
  }

  /**
   * Runs spellcheck on a single interval.
   * 
   * @param start The start offset of the interval.
   * @param end The end offset of the interval.
   * @param controller Author document controller
   * 
   * @return The first problem found or null if no problems were found.
   * 
   * @throws AuthorOperationException If the spellcheck fails.
   */
  private Optional<SpellCheckingProblemInfo> runSpellcheckSingleInterval(
      int start, int end, AuthorDocumentController controller) throws AuthorOperationException {
    logger.debug("Checking interval between " + start + " " + end);

    // Run spellcheck on a slightly larger interval and ignore problems
    // at the boundaries - they may be caused by truncated words.
    int boundary = 50;
    List<TextChunkDescriptor> textDescriptors = 
        spellchecker.getTextDescriptors(
            Math.max(0, start - boundary), 
            Math.min(docLength, end + boundary));
    
    for (TextChunkDescriptor textDescriptor : textDescriptors) {
      try {
        List<SpellCheckingProblemInfo> problems = 
            runSpellcheckTextDescriptor(textDescriptor);
        for (SpellCheckingProblemInfo problem : problems) {
          if (problem.getStartOffset() >= start && problem.getStartOffset() <= end 
              && !ignoredWords.isIgnored(problem, controller)) {
            return Optional.of(problem);
          } else {
            // The given word does not start in our interval. 
            // It will be found again in the next interval.  
          }
        }
      } catch (IOException | BadLocationException e) {
        throw new AuthorOperationException(e.getMessage(), e);
      }
    }
    return Optional.empty();
  }

  /**
   * Runs the spell checker over a single text descriptor.
   * @param textDescriptor The text descriptor.
   * 
   * @return The list of problems.
   * 
   * @throws IOException if the spellcheck fails.
   */
  private List<SpellCheckingProblemInfo> runSpellcheckTextDescriptor(
      TextChunkDescriptor textDescriptor) throws IOException {
    List<SpellCheckingProblemInfo> problems = 
        spellchecker.check(Collections.singletonList(textDescriptor));
    
    return MoreObjects.firstNonNull(problems, 
        Collections.<SpellCheckingProblemInfo>emptyList());
  }

}
