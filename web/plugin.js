/**
 * The id of the spelling dialog action.
 * @type {string} The action id.
 */
var spellingDialogActionId = 'Author/SpellingDialog';

 goog.events.listen(workspace, sync.api.Workspace.EventType.EDITOR_LOADED,
     function(e) {
   var editor = e.editor;

   // Add the action if the editor supports it.
   var editingSupport = editor.getEditingSupport();
   if (editingSupport && editingSupport.getType() === sync.api.Editor.EditorTypes.AUTHOR) {
     editor.getActionsManager().registerAction(spellingDialogActionId, new SpellcheckAction(editor));
     addToMoreToolbar(editor, spellingDialogActionId);
   }
 });


 /**
  * The action that shows a popup and then inserts the text in the pop-up.
  */
 function SpellcheckAction (editor) {
   sync.actions.Action.call(this, {
     keyStrokeStr: 'F7',
     description: tr(msgs.SPELL_CHECK_ACTION_),
     displayName: tr(msgs.SPELL_CHECK_ACTION_)
   });
   this.editor_ = editor;
   this.operationsInvoker_ = editor.getEditingSupport().getOperationsInvoker();
   this.dialog_ = null;

   this.wordInput_ = null;
   this.replaceInput_ = null;
   this.suggestionsBox_ = null;

   this.eventHandler_ = new goog.events.EventHandler(this);
   this.dialogOpenHandler_ = new goog.events.EventHandler(this);

   this.transparenceClass_ = 'man-sp-transparence';

   this.replaceButton_ = null;
   this.replaceAllButton_ = null;
   this.ignoreButton_ = null;

   // If the editor gets disposed, do not do callbacks of spellcheck action requests.
   this.disposed_ = false;
 }
 // shortcut is Meta+L on Mac and Ctrl+L on other platforms.
 SpellcheckAction.prototype = Object.create(sync.actions.Action.prototype);
 SpellcheckAction.prototype.constructor = SpellcheckAction;

 SpellcheckAction.prototype.getLargeIcon = function () {
   var icon = 'SpellCheck24.png';
   if (document.querySelector('.no-app-bar')) {
    icon = 'Blue_SpellCheck24.png';
   }
   return sync.util.computeHdpiIcon('../plugin-resources/man-sp/' + icon);
 };

 SpellcheckAction.prototype.showInfo_ = function (message) {
   this.editor_.problemReporter && this.editor_.problemReporter.showInfo(message);
 };

  /**
   * If the highlight is covered by the dialog, make the dialog partly transparent.
   * @param highlightChunks Chunks of highlighted marker.
   */
  SpellcheckAction.prototype.makeTransparentIfOverSelected_ = function (highlightChunks) {
    if (highlightChunks && highlightChunks.length > 0) {
      var overlap = false;
      var dialogElement = document.querySelector('#manual-spellcheck');
      var dialogRect = dialogElement.getBoundingClientRect();
      goog.array.find(highlightChunks, function (chunk) {
        var chunkRect = chunk.getBoundingClientRect();
        overlap = !(chunkRect.right < dialogRect.left ||
          chunkRect.left > dialogRect.right ||
          chunkRect.bottom < dialogRect.top ||
          chunkRect.top > dialogRect.bottom);
        return overlap;
      });

      if (overlap) {
        goog.dom.classlist.add(dialogElement, this.transparenceClass_);
      } else {
        this.removeTransparency_();
      }
    }
  };

  /**
   * Remove transparency from find/replace dialog.
   * @private
   */
  SpellcheckAction.prototype.removeTransparency_ = function () {
    goog.dom.classlist.remove(this.dialogElement_, this.transparenceClass_);
  };

  /**
   * Find the next error.
   */
 SpellcheckAction.prototype.findNext = function () {
   return this.operationsInvoker_.invoke(
     'com.oxygenxml.webapp.plugins.spellcheck.GoToNextSpellingErrorOperation', {
        params: {ignoredWords: this.editor_.getSpellChecker().getIgnoredWords()}
     })
       .then(this.processNextProblemFindResult_.bind(this))
       .catch(this.handleSpellCheckOperationError_.bind(this));
 };

  /**
   * Handle spellcheck operation error
   *
   * @param {object} err The error.
   */
  SpellcheckAction.prototype.handleSpellCheckOperationError_ = function(err) {
    if (!this.disposed_) {
      console.error(err);
      var errorMessage = tr(msgs.ERROR_COMMUNICATING_WITH_SERVER_);
      this.editor_.problemReporter && this.editor_.problemReporter.showError(errorMessage, true);
    }
  };

  /**
   * Clear spellcheck suggestion.
   *
   * @private
   */
  SpellcheckAction.prototype.clearSpellCheckSuggestions_ = function() {
    this.replaceInput_.value = '';
    goog.dom.removeChildren(this.suggestionsBox_);
  };

  /**
   * Populate the suggestions select with options.
   * @param {Array<String>} suggestions The list of suggestions.
   * @private
   */
 SpellcheckAction.prototype.displaySuggestions_ = function (suggestions) {
   // In case of no suggestions, just clear replace with input and suggestions box.
   this.replaceInput_.value = '';
   goog.dom.removeChildren(this.suggestionsBox_);

   if (suggestions.length) {
     var suggestionElements = [];
     for (var i = 0; i < suggestions.length; i++) {
       suggestionElements.push(goog.dom.createDom('option', 'man-sp-suggestion', suggestions[i]));
     }
     // First suggestion gets added to the replace input, also marked as selected.
     this.replaceInput_.value = suggestions[0];
     suggestionElements[0].setAttribute('selected', 'selected');
     goog.dom.append(this.suggestionsBox_, suggestionElements);
   }
 };

  /**
   * If the user clicked on a suggestion, set its value to the replace input.
   * @private
   */
 SpellcheckAction.prototype.suggestionSelected_ = function () {
   this.replaceInput_.value = this.suggestionsBox_.value;
 };

  /**
   * Create the manual spellcheck dialog.
   * @return {sync.api.Dialog} The dialog.
   * @private
   */
 SpellcheckAction.prototype.createDialog_ = function () {
   var dialog = workspace.createDialog('manual-spellcheck', true);
   dialog.setPreferredSize(370, 340);
   dialog.setHasTitleCloseButton(true);
   dialog.setBackgroundElementOpacity(0);
   dialog.setTitle(tr(msgs.SPELLING_));
   dialog.setResizable(true);
   dialog.setButtonConfiguration([]);
   return dialog;
 };

  /**
   * Create a button element for the dialog.
   * @param {string} name The button name.
   * @param {string} caption The text of the button.
   * @return {Element} The button element.
   * @private
   */
  SpellcheckAction.prototype.createButton_ = function (name, caption) {
    var button = goog.dom.createDom('button', { className: 'man-sp-button oxy-button oxy-button--small' }, caption);
    goog.dom.dataset.set(button, 'spButton', name);
    return button;
  };

  /**
   * Add the contents of the dialog.
   * @private
   */
 SpellcheckAction.prototype.createDialogContents_ = function () {
   var createDom = goog.dom.createDom;
   this.wordInput_ = createDom('input', { id: 'man-sp-word', className: 'man-sp-input', type: 'text' });
   this.wordInput_.setAttribute('readonly', 'true');
   this.replaceInput_ = createDom('input', { id: 'man-sp-replace-with', className: 'man-sp-input', type: 'text' });
   this.suggestionsBox_ = createDom('select', { id: 'man-sp-suggestions', size: 6 });

   var labelClass = 'man-sp-label';
   var suggestionsLabel = goog.dom.createDom('label', { className: labelClass }, tr(msgs.SUGGESTIONS_) + ':');
   suggestionsLabel.setAttribute('for', 'man-sp-suggestions');
   var inputsColumn = createDom('div', 'man-sp-col man-inputs',
     createDom('div', 'man-sp',
       goog.dom.createDom('div', {style: 'position: relative;'},
         goog.dom.createDom('label', { className: labelClass, for: 'man-sp-word' }, tr(msgs.MISSPELLED_WORD_) + ':')
       ),
       this.wordInput_,
       goog.dom.createDom('label', { className: labelClass },
         tr(msgs.REPLACE_WITH_) + ':',
         this.replaceInput_
       ),
       suggestionsLabel,
       this.suggestionsBox_
     )
   );

   this.ignoreButton_ = this.createButton_('ignore', tr(msgs.IGNORE_));
   var ignoreAllButton = this.createButton_('ignore_all', tr(msgs.IGNORE_ALL_));
   this.replaceButton_ = this.createButton_('replace', tr(msgs.REPLACE_));
   this.replaceAllButton_ = this.createButton_('replace_all', tr(msgs.REPLACE_ALL_));
   
   goog.dom.classlist.add(this.replaceButton_, 'oxy-button--primary');

   var buttonsColumn = createDom('div', 'man-sp-col man-buttons',
     this.replaceButton_,
     this.replaceAllButton_,
     createDom('div', 'man-sp-divider'),
     this.ignoreButton_,
     ignoreAllButton
   );

   var dialogElement = this.dialog_.getElement();
   dialogElement.setAttribute('id', 'manual-spellcheck-container');

   goog.dom.append(dialogElement,
     inputsColumn,
     buttonsColumn
   );

   goog.events.listen(buttonsColumn, goog.events.EventType.CLICK, goog.bind(this.clickOnButtons_, this));
 };

  /**
   * Show the dialog.
   * @private
   */
  SpellcheckAction.prototype.showDialog_ = function () {
    if (!this.dialog_) {
      this.dialog_ = this.createDialog_();
      var toolbarButton = document.querySelector('[name="' + spellingDialogActionId + '"]');
      if (toolbarButton) {
        var position = goog.style.getPageOffset(toolbarButton);
        this.dialog_.setPosition(position.x , (position.y + toolbarButton.clientHeight));
      }

      this.createDialogContents_();
      var suggestionsBox = this.suggestionsBox_;
      goog.events.listen(suggestionsBox, goog.events.EventType.CHANGE,
        goog.bind(this.suggestionSelected_, this));
      goog.events.listen(suggestionsBox, goog.events.EventType.FOCUSIN, goog.bind(function () {
        var replaceWithValue = this.replaceInput_.value;
        // In case the replace with input value is the same as one of the options, update the selected option.
        // If replace with input is not one of the options, unselect options.
        if (replaceWithValue !== suggestionsBox.value) {
          suggestionsBox.selectedIndex = goog.array.findIndex(suggestionsBox.options, function (o) {
            return o.textContent === replaceWithValue;
          });
        }
      }, this));

      this.eventHandler_
        .listen(this.dialog_.getEventTarget(), goog.ui.PopupBase.EventType.SHOW, goog.bind(this.afterShow_, this))
        .listen(this.dialog_.getEventTarget(), goog.ui.PopupBase.EventType.BEFORE_HIDE, goog.bind(this.beforeHide_, this));
    }

    this.setSpellCheckButtonsEnabled_(false);
    this.wordInput_.value = '';
    this.clearSpellCheckSuggestions_();
    this.dialog_.show();
  };

  /**
   * Clear spellcheck context information.
   *
   * @return {Promise} when the spellcheck information is cleared from the server.
   *
   * @private
   */
  SpellcheckAction.prototype.clearSpellcheckContextInformation_ = function() {
    return this.operationsInvoker_.invoke(
        'com.oxygenxml.webapp.plugins.spellcheck.ClearSpellingContextInformationOperation',
        {params: {}, options: {background: true}});
  };

  /**
   * Handle click in the buttons column.
   * @param {goog.events.Event} e The click event.
   * @private
   */
  SpellcheckAction.prototype.clickOnButtons_ = function (e) {
    var button = goog.dom.getAncestorByClass(e.target, 'man-sp-button');
    if (button) {
      var buttonType = goog.dom.dataset.get(button, 'spButton');
      if (buttonType === 'ignore') {
        // just go to next marker.
        this.scheduleDocumentTransaction_(this.ignore_, this);
      } else if (buttonType === 'ignore_all') {
        this.scheduleDocumentTransaction_(this.ignoreAll_, this);
      } else if (buttonType === 'replace') {
        this.scheduleDocumentTransaction_(this.replace_, this);
      } else if (buttonType === 'replace_all') {
        this.scheduleDocumentTransaction_(function() {
          this.replace_(true)
        }, this);
      }
    }
  };

  /**
   * Ignore all occurences of the word.
   * @return {Promise}
   * @private
   */
  SpellcheckAction.prototype.ignoreAll_ = function () {
    var language = this.language_;
    var word = this.word_;
    // Add the word to the ignore list for the language.
    this.editor_.getSpellChecker().addIgnoredWord(language, word);
    return this.findNext();
  };

  /**
   * Ignore current spellcheck problem.
   *
   * @private
   */
  SpellcheckAction.prototype.ignore_ = function () {
    return this.operationsInvoker_.invoke(
      'com.oxygenxml.webapp.plugins.spellcheck.IgnoreCurrentAndFindNextSpellingOperation', {
        params: {ignoredWords: this.editor_.getSpellChecker().getIgnoredWords()}
      })
        .then(this.processNextProblemFindResult_.bind(this))
        .catch(this.handleSpellCheckOperationError_.bind(this));
  };

  /**
   * Replace an error with the selected value.
   *
   * @param {boolean=} all True to replace all occurrences.
   *
   * @private
   */
  SpellcheckAction.prototype.replace_ = function (all) {
    return this.operationsInvoker_.invoke(
      'com.oxygenxml.webapp.plugins.spellcheck.ReplaceAndFindNextSpellingOperation', {
          params: {
            newWord: this.replaceInput_.value,
            replaceAll : !!all,
            ignoredWords: this.editor_.getSpellChecker().getIgnoredWords()
          }
      })
        .then(function(resultString) {
          /** @type {{wordChanged: boolean=}} */
          var result = resultString ? JSON.parse(resultString) : {};
          if (result.wordChanged) {
            return this.showChangedWordWarning_()
                .then(this.findNext.bind(this));
          } else {
            this.processNextProblemFindResult_(resultString);
          }
        }.bind(this))
        .catch(this.handleSpellCheckOperationError_.bind(this));
  };

  /**
   * Show a warning that a word was changed and thus, the replace could not be performed.
   * @private
   */
  SpellcheckAction.prototype.showChangedWordWarning_ = function () {
    return Promise.resolve()
        .then(this.createChangedWordWarningDialog_.bind(this))
        .then(this.waitForDialogSelect_.bind(this))
  };

  /**
   * @return {sync.api.Dialog} The warning dialog.
   *
   * @private
   */
  SpellcheckAction.prototype.createChangedWordWarningDialog_ = function () {
    var dialog = workspace.createDialog();
    dialog.setTitle(tr(msgs.SPELL_CHECK_ACTION_));
    goog.dom.appendChild(dialog.getElement(),
        goog.dom.createDom('div', '', tr(msgs.THE_WORD_HAS_CHANGED_)));
    dialog.setButtonConfiguration(sync.api.Dialog.ButtonConfiguration.OK);
    dialog.getElement().setAttribute('id', 'word-changed-warn-container');
    return dialog;
  };

  /**
   * Show the dialog and waits for user input.
   *
   * @param {sync.api.Dialog} dialog The dialog to wait on.
   * @return {Promise} a promise that resolves when the user presses a button in the dialog.
   *
   * @private
   */
    SpellcheckAction.prototype.waitForDialogSelect_ = function (dialog) {
      return new Promise(function(resolve) {
        dialog.show();
        dialog.onSelect(function() {
          resolve();
          dialog.dispose();
        });
      })
    };

  /**
   * Process the result of finding next spellcheck problem.
   *
   * @param {String} nextProblemDescrString Next spellcheck problem descriptor.
   *
   * @private
   */
  SpellcheckAction.prototype.processNextProblemFindResult_ = function(nextProblemDescrString) {
    // If dialog was closed or disposed, do nothing.
    if (!this.dialog_ || !this.dialog_.isVisible() || this.disposed_) {
      return;
    }
    /**
     * @type {{language: string, word: string, suggestions: [string]}}
     */
    var nextSpellCheckDescr;
    try {
      nextSpellCheckDescr = JSON.parse(nextProblemDescrString);
    } catch (e) {
      nextSpellCheckDescr = {};
    }

    var word = nextSpellCheckDescr.word;
    this.wordInput_.value = word || '';
    if (word) {
      this.word_ = word;
      this.language_ = nextSpellCheckDescr.language;
      var suggestions = nextSpellCheckDescr.suggestions;
      if (suggestions) {
        this.displaySuggestions_(suggestions);
      }
      // If selection is now in readonly content, disable replace buttons.
      this.setSpellCheckButtonsEnabled_(true);
      var editorReadOnlyStatus = this.editor_.getReadOnlyState().readOnly;
      var selectionInReadOnlyContent = this.editor_.getSelectionManager().evalSelectionFunction(sync.util.isInReadOnlyContent);
      if (editorReadOnlyStatus || selectionInReadOnlyContent) {
        this.replaceButton_.disabled = true;
        this.replaceAllButton_.disabled = true;
      }
    } else {
      this.clearSpellCheckSuggestions_();
      this.showInfo_(tr(msgs.NO_SPELLING_ERRORS_FOUND_));
    }

    this.replaceInput_.focus();


    var selection = this.editor_.getSelectionManager().getSelection();
    this.editor_.getSelectionManager().scrollSelectionIntoView(selection);

    // Consider only non-empty selection placeholder chunks for the dialog overlap check.
    var selectedMarkerChunks = document.querySelectorAll('.oxy-selected');
    this.makeTransparentIfOverSelected_(selectedMarkerChunks);
  };

  /**
   * Activate/inactivate the spellcheck.
   *
   * @param enabled True to enable all spellcheck buttons, false for disable.
   */
  SpellcheckAction.prototype.setSpellCheckButtonsEnabled_ = function(enabled) {
    var buttons = this.dialog_.getElement().getElementsByTagName('button');
    goog.array.forEach(buttons, function (button) {
      button.disabled = !enabled;
    });
  };


  /**
   * Save last position, clear dialog open listeners and selected markers.
   * @private
   */
  SpellcheckAction.prototype.beforeHide_ = function () {
    // noinspection JSIgnoredPromiseFromCall
    this.clearSpellcheckContextInformation_();
    this.dialogOpenHandler_.removeAll();
  };

  /**
   * Set to default position first time, set dialog open listeners.
   * @private
   */
  SpellcheckAction.prototype.afterShow_ = function () {
    if (!this.dialogElement_) {
      this.dialogElement_ = document.querySelector('#manual-spellcheck');
    }

    // Register some listeners only for when dialog is shown.
    this.dialogOpenHandler_
      .listen(this.dialogElement_, goog.events.EventType.CLICK, goog.bind(this.removeTransparency_, this), true)
      .listen(this.replaceInput_, goog.events.EventType.KEYUP, goog.bind(this.doActionOnEnter_, this))
      .listen(this.suggestionsBox_, goog.events.EventType.KEYUP, goog.bind(this.doActionOnEnter_, this));
  };

  /**
   * Trigger actions on Enter while in the replace with input or suggestions select.
   * @param {goog.events.KeyEvent} e The keyup event.
   * @private
   */
  SpellcheckAction.prototype.doActionOnEnter_ = function (e) {
    // On Enter do Replace if enabled, Ignore otherwise.
    if (e.keyCode === goog.events.KeyCodes.ENTER) {
      if (this.replaceButton_.disabled === false) {
        this.scheduleDocumentTransaction_(this.replace_, this);
      } else if (this.ignoreButton_.disabled === false) {
        this.scheduleDocumentTransaction_(this.findNext, this);
      }
    }
  };

  /**
   * Schedule a document transaction.
   * @param {function():Promise} transaction The transaction to execute.
   * @param {object} context The context object.
   * @private
   */
  SpellcheckAction.prototype.scheduleDocumentTransaction_ = function (transaction, context) {
    this.editor_.getEditingSupport().scheduleDocumentTransaction(function() {
      this.setSpellCheckButtonsEnabled_(false);
      return transaction.call(context);
    }.bind(this));
  };

 // The actual action execution.
 SpellcheckAction.prototype.actionPerformed = function(callback) {
   this.showDialog_();
   callback && callback();
   this.scheduleDocumentTransaction_(this.findNext, this);
 };

  /**
   * Clean up when switching editor from DMM.
   */
  SpellcheckAction.prototype.dispose = function () {
    this.disposed_ = true;
    this.dialog_ && this.dialog_.dispose();
    this.dialogOpenHandler_.dispose();
    this.eventHandler_.dispose();
  };


  function addToMoreToolbar(editor, actionId) {
    goog.events.listen(editor, sync.api.Editor.EventTypes.ACTIONS_LOADED, function(e) {
      var actionsConfig = e.actionsConfiguration;
      var builtinToolbar = null;
      if (actionsConfig.toolbars) {
        for (var i = 0; i < actionsConfig.toolbars.length; i++) {
          var toolbar = actionsConfig.toolbars[i];
          if (toolbar.name === "Builtin") {
            builtinToolbar = toolbar;
          }
        }
      }

      if (builtinToolbar) {
        var moreElement = goog.array.find(builtinToolbar.children, function (el) { return el.name === 'More...' });
        goog.array.insertBefore(builtinToolbar.children, {
          id: actionId,
          type: "action"
        }, moreElement);
      }
    });
  }
