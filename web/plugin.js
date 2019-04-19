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
 var SpellcheckAction = function(editor) {
   sync.actions.AbstractAction.call(this, 'M1 F7');
   this.editor_ = editor;
   this.dialog_ = null;

   this.wordInput_ = null;
   this.replaceInput_ = null;
   this.suggestionsBox_ = null;
 };
 // shortcut is Meta+L on Mac and Ctrl+L on other platforms.
 SpellcheckAction.prototype = Object.create(sync.actions.AbstractAction.prototype);
 SpellcheckAction.prototype.constructor = SpellcheckAction;

 SpellcheckAction.prototype.getDisplayName = function() {
   return tr(msgs.SPELL_CHECK_);
 };

 SpellcheckAction.prototype.findNext = function () {
   var actionsManager = this.editor_.getActionsManager();
   actionsManager.invokeOperation(
     'com.oxygenxml.webapp.plugins.spellcheck.GoToNextSpellingErrorOperation', {
       'fromCaret' : true,
       'ignoredWords': this.editor_.getSpellChecker().getIgnoredWords()
     }, goog.bind(function(err, resultString) {
       var previouslySelected = document.querySelectorAll('.' + selectedMarkerClass);
       for (var j = 0; j < previouslySelected.length; j++) {
          goog.dom.classlist.remove(previouslySelected[j], selectedMarkerClass);
       }

       var selectedNode = window.getSelection().focusNode;

       var markerAncestor = goog.dom.getAncestorByClass(window.getSelection().baseNode, 'spellcheckingError');
       if (markerAncestor) {
         goog.dom.classlist.add(markerAncestor, selectedMarkerClass);
       }
       if (selectedNode) {
         if (selectedNode.nodeType === 3) {
           selectedNode = selectedNode.parentNode;
         }
         var rect = selectedNode.getBoundingClientRect() || {};
         // If the selected node is hidden, try to toggle the current fold.
         if (rect.height === 0) {
           actionsManager.getActionById('Author/ToggleFold').actionPerformed(function() {
             selectedNode.scrollIntoView();
           })
         } else {
           selectedNode.scrollIntoView();
         }
       }
       var result = JSON.parse(resultString);

       var word = result.word;
       if (word) {
         this.wordInput_.value = word;

         this.word_ = word;
         this.language_ = result.language;
         this.suggestions_ = result.suggestions;
         this.startOffset_ = result.startOffset;
         this.endOffset_ = result.endOffset;
       }
       var suggestions = result.suggestions;
       if (suggestions && suggestions.length) {
         this.displaySuggestions_(suggestions);
       }
    }, this));
 };

 SpellcheckAction.prototype.displaySuggestions_ = function (suggestions) {
   var suggestionElements = [];
   for (var i = 0; i < suggestions.length; i++) {
      suggestionElements.push(goog.dom.createDom('option', 'man-sp-suggestion', suggestions[i]));
   }
   // First suggestion gets added to the replace input, also marked as selected.
   this.replaceInput_.value = suggestions[0];
   /*goog.dom.classlist.add(suggestionElements[0], 'man-sp-selected');*/
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

 SpellcheckAction.prototype.beforeHide_ = function () {
   var fakeSpellcheckingSelections = document.querySelectorAll('.' + selectedMarkerClass);
   for (var j = 0; j < fakeSpellcheckingSelections.length; j++) {
     goog.dom.classlist.remove(fakeSpellcheckingSelections[j], selectedMarkerClass);
   }
 };
  SpellcheckAction.prototype.showDialog_ = function () {
    var dialog = this.dialog_;
    if (!dialog) {
      dialog = workspace.createDialog('manual-spellcheck', true);
      dialog.setPreferredSize(380, 480);
      dialog.setTitle(tr(msgs.SPELLING_));
      dialog.setResizable(true);
      dialog.setButtonConfiguration([]);

      var spDialogEventTarget = dialog.getEventTarget();
      goog.events.listen(spDialogEventTarget, goog.ui.PopupBase.EventType.BEFORE_HIDE, goog.bind(this.beforeHide_, this));
      //this.eventHandler_.listen()

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
          createLabel(this.wordInput_, tr(msgs.WORD_)),
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
        ignoreButton,
        ignoreAllButton,
        replaceButton,
        replaceAllButton
      );

      dialog.getElement().setAttribute('id', 'manual-spellcheck-container');


      goog.dom.append(dialog.getElement(),
        inputsColumn,
        buttonsColumn
      );

      var getErrorPosition = function () {
        var selection = sync.select.getSelection();
        // todo: Show a warning if the selected result is in a read-only part of the document.
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
            // todo: grab replace action non-api or just use the operation and not care?
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

      this.dialog_ = dialog;
    }

    dialog.show();
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
       builtinToolbar.children.push({
         id: actionId,
         type: "action"
       });
     }
   });
 }
})();
