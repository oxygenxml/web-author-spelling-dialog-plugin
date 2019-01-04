(function () {

  var cssFile;
  cssFile = goog.dom.createDom('link');
  cssFile.rel = "stylesheet";
  cssFile.type = "text/css";
  cssFile.href = "../plugin-resources/man-sp/custom.css";
  goog.dom.appendChild(document.head, cssFile);

  console.log('manual spellcheck plugin loaded successfully');
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
 };
 // shortcut is Meta+L on Mac and Ctrl+L on other platforms.
 SpellcheckAction.prototype = Object.create(sync.actions.AbstractAction.prototype);
 SpellcheckAction.prototype.constructor = SpellcheckAction;

 SpellcheckAction.prototype.getDisplayName = function() {
   return 'Spellcheck';
 };

 SpellcheckAction.prototype.findNext = function () {
   var actionsManager = this.editor_.getActionsManager();
   actionsManager.invokeOperation(
     'com.oxygenxml.webapp.plugins.spellcheck.GoToNextSpellingErrorOperation', {
       'fromCaret' : true,
       'ignoredWords': this.editor_.getSpellChecker().getIgnoredWords()
     }, goog.bind(function(err, resultString) {
       var selectedNode = window.getSelection().focusNode;
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
       console.log(result);

       var word = result.word;
       if (word) {
         this.wordInput_.value = word;
       }
       var suggestions = result.suggestions;
       if (suggestions) {
         this.displaySuggestions_(suggestions);
       }
    }, this));
 };

 SpellcheckAction.prototype.displaySuggestions_ = function (suggestions) {
   var suggestionElements = [];
   for (var i = 0; i < suggestions.length; i++) {
      suggestionElements.push(goog.dom.createDom('div', '', suggestions[i]));
   }
   goog.dom.removeChildren(this.suggestionsBox_);
   goog.dom.append(this.suggestionsBox_, suggestionElements);
 };

  SpellcheckAction.prototype.showDialog_ = function () {
    var dialog = this.dialog_;
    if (!dialog) {
      dialog = workspace.createDialog('spellcheck', true);
      dialog.setPreferredSize(450, 500);
      dialog.setTitle('Spelling'/*msgs.SPELLING_*/);
      dialog.setResizable(true);
      dialog.setButtonConfiguration([]);

      var createDom = goog.dom.createDom;

      this.wordInput_ = createDom('input', { id: 'man-sp-word', className: 'man-sp-input', type: 'text'});
      this.suggestionsBox_ = createDom('div', {
          id: 'man-sp-suggestions',
          style: 'border: 1px solid lightgray;'
        }
      );

      var inputsColumn = createDom('div', 'man-sp-col man-inputs',
        createDom('div', 'man-sp',
          createDom('label', { className: 'man-sp-label', for: 'man-sp-word' }, 'Word'/*tr(msgs.WORD_)*/ + ':'),
          this.wordInput_,

          createDom('label', { className: 'man-sp-label', for: 'man-sp-replace-with' }, 'Replace with'/*tr(msgs.REPLACE_WITH_)*/ + ':'),
          createDom('input', { id: 'man-sp-replace-with', className: 'man-sp-input', type: 'text' }),

          createDom('label', {
            className: 'man-sp-label',
            for: 'man-sp-suggestions'
          }, 'Suggestions'/*tr(msgs.SUGGESTIONS_)*/ + ':'),
          this.suggestionsBox_
        )
      );
      var buttonsColumn = createDom('div', 'man-sp-col man-buttons',
        createDom('div', 'man-sp-button', 'Ignore'), //todo: translate 'em /*tr(msgs.IGNORE_)*/
        createDom('div', 'man-sp-button' , 'Ignore All'),
        createDom('div', 'man-sp-button' , 'Replace'),
        createDom('div', 'man-sp-button' , 'Replace All')
      );

      dialog.getElement().setAttribute('id', 'manual-spellcheck-container');


      goog.dom.append(dialog.getElement(),
        inputsColumn,
        buttonsColumn
      );

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
