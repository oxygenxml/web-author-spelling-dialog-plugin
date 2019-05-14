(function () {

  var cssFile;
  cssFile = goog.dom.createDom('link');
  cssFile.rel = "stylesheet";
  cssFile.type = "text/css";
  cssFile.href = "../plugin-resources/man-sp/custom.css";
  goog.dom.appendChild(document.head, cssFile);

  var selectedMarkerClass = 'spelling-selected';
 goog.events.listenOnce(workspace, sync.api.Workspace.EventType.BEFORE_EDITOR_LOADED,
     function(e) {
   var editor = e.editor;

   // Register the newly created action.
   editor.getActionsManager().registerAction('spellcheck', new SpellcheckAction(editor));
   addToMoreToolbar(editor, 'spellcheck');
 });


 /**
  * The action that shows a popup and then inserts the text in the pop-up.
  */
 function SpellcheckAction (editor) {
   sync.actions.AbstractAction.call(this, 'F7');
   this.editor_ = editor;
   this.dialog_ = null;

   this.wordInput_ = null;
   this.replaceInput_ = null;
   this.suggestionsBox_ = null;

   this.eventHandler_ = new goog.events.EventHandler(this);
   this.dialogOpenHandler_ = new goog.events.EventHandler(this);

   this.lastDialogPosition_ = null;

   this.transparenceClass_ = 'man-sp-transparence';
 }
 // shortcut is Meta+L on Mac and Ctrl+L on other platforms.
 SpellcheckAction.prototype = Object.create(sync.actions.AbstractAction.prototype);
 SpellcheckAction.prototype.constructor = SpellcheckAction;

 SpellcheckAction.prototype.getLargeIcon = function () {
   return sync.util.computeHdpiIcon('../plugin-resources/man-sp/SpellCheck24.png');
 };

  SpellcheckAction.prototype.getDescription = function() {
    return tr(msgs.SPELL_CHECK_ACTION_);
  };

 SpellcheckAction.prototype.getDisplayName = function() {
   return tr(msgs.SPELL_CHECK_ACTION_);
 };

 SpellcheckAction.prototype.showInfo_ = function (message) {
   this.editor_.problemReporter && this.editor_.problemReporter.showInfo(message);
 };

  /**
   * Make sure the selected error is visible - scroll to it and/or expand its fold.
   * @private
   */
 SpellcheckAction.prototype.scrollIntoViewIfNeeded_ = function () {
   var selectedNode = window.getSelection().focusNode;
   if (selectedNode) {
     if (selectedNode.nodeType === 3) {
       selectedNode = selectedNode.parentNode;
     }
     var rect = selectedNode.getBoundingClientRect() || {};
     // If the selected node is hidden, try to toggle the current fold.
     if (rect.height === 0) {
       this.editor_.getActionsManager().getActionById('Author/ToggleFold').actionPerformed(function() {
         selectedNode.scrollIntoView(false);
       })
     } else {
       selectedNode.scrollIntoView(false);
     }
   }
 };

  /**
   * Clear selected spellchecking error markers.
   * @private
   */
 SpellcheckAction.prototype.clearSelectedMarkers_ = function () {
   var fakeSpellcheckingSelections = document.querySelectorAll('.' + selectedMarkerClass);
   for (var j = 0; j < fakeSpellcheckingSelections.length; j++) {
     goog.dom.classlist.remove(fakeSpellcheckingSelections[j], selectedMarkerClass);
   }
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

 SpellcheckAction.prototype.findNext = function () {
   var actionsManager = this.editor_.getActionsManager();
   actionsManager.invokeOperation(
     'com.oxygenxml.webapp.plugins.spellcheck.GoToNextSpellingErrorOperation', {
       'fromCaret' : true,
       'ignoredWords': this.editor_.getSpellChecker().getIgnoredWords()
     }, goog.bind(function(err, resultString) {

       this.clearSelectedMarkers_();

       var markerAncestor = goog.dom.getAncestorByClass(window.getSelection().baseNode, 'spellcheckingError');
       if (markerAncestor) {
         goog.dom.classlist.add(markerAncestor, selectedMarkerClass);
         this.scrollIntoViewIfNeeded_();
         this.makeTransparentIfOverSelected_([markerAncestor]);
       }

       var result;
       try {
         result = JSON.parse(resultString);
       } catch (e) {
         result = {};
       }

       var word = result.word;
       this.wordInput_.value = word || '';
       if (word) {
         this.word_ = word;
         this.language_ = result.language;
         var suggestions = result.suggestions;
         if (suggestions && suggestions.length) {
           this.displaySuggestions_(suggestions);
         }
       } else {
         this.replaceInput_.value = '';
         goog.dom.removeChildren(this.suggestionsBox_);

         this.showInfo_(tr(msgs.NO_SPELLING_ERRORS_FOUND_));
       }
    }, this));
 };

  /**
   * Populate the suggestions select with options.
   * @param {Array<String>} suggestions The list of suggestions.
   * @private
   */
 SpellcheckAction.prototype.displaySuggestions_ = function (suggestions) {
   var suggestionElements = [];
   for (var i = 0; i < suggestions.length; i++) {
      suggestionElements.push(goog.dom.createDom('option', 'man-sp-suggestion', suggestions[i]));
   }
   // First suggestion gets added to the replace input, also marked as selected.
   this.replaceInput_.value = suggestions[0];
   suggestionElements[0].setAttribute('selected', 'selected');

   goog.dom.removeChildren(this.suggestionsBox_);
   goog.dom.append(this.suggestionsBox_, suggestionElements);
 };

  /**
   * If the user clicked on a suggestion, set its value to the replace input.
   * @private
   */
 SpellcheckAction.prototype.suggestionSelected_ = function () {
   this.replaceInput_.value = this.suggestionsBox_.value;
 };

  SpellcheckAction.prototype.showDialog_ = function () {
    var dialog = this.dialog_;
    if (!dialog) {
      dialog = workspace.createDialog('manual-spellcheck', true);
      dialog.setPreferredSize(350, 340);
      dialog.setTitle(tr(msgs.SPELLING_));
      dialog.setResizable(true);
      dialog.setButtonConfiguration([]);

      var createDom = goog.dom.createDom;
      this.wordInput_ = createDom('input', { id: 'man-sp-word', className: 'man-sp-input', type: 'text' });
      this.wordInput_.setAttribute('readonly', 'true');
      this.replaceInput_ = createDom('input', { id: 'man-sp-replace-with', className: 'man-sp-input', type: 'text' });
      this.suggestionsBox_ = createDom('select', {
          id: 'man-sp-suggestions',
          size: 6
        }
      );

      var createLabel = function (inputElement, caption) {
        return goog.dom.createDom('label', { className: 'man-sp-label' },
          caption + ':',
          inputElement
        )
      };

      var suggestionsLabel = createLabel(null, tr(msgs.SUGGESTIONS_));
      suggestionsLabel.setAttribute('for', 'man-sp-suggestions');
      var inputsColumn = createDom('div', 'man-sp-col man-inputs',
        createDom('div', 'man-sp',
          goog.dom.createDom('div', {style: 'position: relative;'},
            goog.dom.createDom('label', { className: 'man-sp-label', for: 'man-sp-word' }, tr(msgs.MISSPELLED_WORD_) + ':')
          ),
          this.wordInput_,
          createLabel(this.replaceInput_, tr(msgs.REPLACE_WITH_)),
          suggestionsLabel,
          this.suggestionsBox_
        )
      );

      var createButton = function (name, caption) {
        var button = goog.dom.createDom('button', { className: 'man-sp-button ' }, caption);
        goog.dom.dataset.set(button, 'spButton', name);
        return button;
      };
      var ignoreButton = createButton('ignore', tr(msgs.IGNORE_));
      var ignoreAllButton = createButton('ignore_all', tr(msgs.IGNORE_ALL_));
      var replaceButton = createButton('replace', tr(msgs.REPLACE_));
      var replaceAllButton = createButton('replace_all', tr(msgs.REPLACE_ALL_));


      var buttonsColumn = createDom('div', 'man-sp-col man-buttons',
        replaceButton,
        replaceAllButton,
        ignoreButton,
        ignoreAllButton
      );

      var dialogElement = dialog.getElement();
      dialogElement.setAttribute('id', 'manual-spellcheck-container');


      goog.dom.append(dialogElement,
        inputsColumn,
        buttonsColumn
      );

      var getErrorPosition = function () {
        var selection = sync.select.getSelection();
        // todo: (WA-2981) Show a warning if the selected result is in a read-only part of the document.
        // Maybe just ignore it in results altogether.
        /*if (sync.select.evalSelectionFunction(sync.util.isInReadOnlyContent)) {
          return null;
        }*/
        return selection.start.toRelativeContentPosition();
      };

      goog.events.listen(this.suggestionsBox_, goog.events.EventType.CHANGE, goog.bind(this.suggestionSelected_, this));
      goog.events.listen(buttonsColumn, goog.events.EventType.CLICK, goog.bind(function (e) {
        var button = goog.dom.getAncestorByClass(e.target, 'man-sp-button');
        if (button) {
          var buttonType = goog.dom.dataset.get(button, 'spButton');
          if (buttonType === 'ignore') {
            // just go to next marker.
          } else if (buttonType === 'ignore_all') {
            var language = this.language_;
            var word = this.word_;
            // Add the word to the ignore list for the language.
            this.editor_.getSpellChecker().addIgnoredWord(language, word);
          } else if (buttonType === 'replace') {
            var replaceAction = new sync.spellcheck.SpellCheckReplaceAction(
              this.editor_.getController(),
              -1,
              -1,
              this.word_,
              this.replaceInput_.value,
              getErrorPosition()
            );
            replaceAction.actionPerformed();
          } else if (buttonType === 'replace_all') {
            sync.rest.callAsync(RESTFindReplaceSupport.replaceAllInDocument, {
              docId: this.editor_.getController().docId,
              textToFind: this.word_,
              textToReplace: this.replaceInput_.value,
              matchCase: true,
              wholeWords: true
            }).then(goog.bind(function (e) {
              this.editor_.getController().applyUpdate_(e);
            }, this));
          }
          this.findNext();
        }
      }, this));

      this.eventHandler_
        .listen(dialog.getEventTarget(), goog.ui.PopupBase.EventType.SHOW, goog.bind(this.afterShow_, this))
        .listen(dialog.getEventTarget(), goog.ui.PopupBase.EventType.BEFORE_HIDE, goog.bind(this.beforeHide_, this));
      this.dialog_ = dialog;
    }

    dialog.show();
  };


  /**
   * Save last position, clear dialog open listeners and selected markers.
   * @private
   */
  SpellcheckAction.prototype.beforeHide_ = function () {
    // Save dialog sizes and position for the next time it gets shown.
    this.lastDialogPosition_ = goog.style.getPageOffset(this.dialogElement_);
    this.clearSelectedMarkers_();
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
    if (!this.lastDialogPosition_) {
      var toolbarButton = document.querySelector('[name="spellcheck"]');
      if (toolbarButton) {
        var position = goog.style.getPageOffset(toolbarButton);
        goog.style.setPosition(this.dialogElement_, position.x , (position.y + toolbarButton.clientHeight));
      }
    } else {
      goog.style.setPosition(this.dialogElement_, this.lastDialogPosition_.x, this.lastDialogPosition_.y);
    }

    // Register some listeners only for when dialog is shown.
    this.dialogOpenHandler_.listen(this.dialogElement_, goog.events.EventType.CLICK,
      goog.bind(this.removeTransparency_, this), true);
  };
 // The actual action execution.
 SpellcheckAction.prototype.actionPerformed = function(callback) {
   this.showDialog_();
   this.findNext();
   callback && callback();
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
})();
