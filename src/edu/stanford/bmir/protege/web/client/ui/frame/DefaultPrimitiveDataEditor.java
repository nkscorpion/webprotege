package edu.stanford.bmir.protege.web.client.ui.frame;

import com.google.common.base.Optional;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import edu.stanford.bmir.protege.web.client.rpc.EntityLookupService;
import edu.stanford.bmir.protege.web.client.rpc.data.EntityData;
import edu.stanford.bmir.protege.web.client.rpc.data.PropertyEntityData;
import edu.stanford.bmir.protege.web.client.rpc.data.PropertyType;
import edu.stanford.bmir.protege.web.client.rpc.data.ValueType;
import edu.stanford.bmir.protege.web.client.ui.library.common.EventStrategy;
import edu.stanford.bmir.protege.web.client.ui.library.suggest.EntitySuggestOracle;
import edu.stanford.bmir.protege.web.client.ui.library.suggest.EntitySuggestion;
import edu.stanford.bmir.protege.web.client.ui.library.text.ExpandingTextBox;
import edu.stanford.bmir.protege.web.client.ui.library.text.ExpandingTextBoxMode;
import edu.stanford.bmir.protege.web.shared.DataFactory;
import edu.stanford.bmir.protege.web.shared.DirtyChangedEvent;
import edu.stanford.bmir.protege.web.shared.DirtyChangedHandler;
import edu.stanford.bmir.protege.web.shared.PrimitiveType;
import edu.stanford.bmir.protege.web.shared.entity.*;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 03/12/2012
 * <p>
 * An editor for {@link OWLPrimitiveData} objects.  The editor supports auto-completion using the
 * {@link EntityLookupService} and has an option to allow the creation of new primitives.
 * </p>
 */
public class DefaultPrimitiveDataEditor extends PrimitiveDataEditor implements HasEnabled {

    public static final int SUGGEST_LIMIT = 20;

    public static final String ERROR_STYLE_NAME = "web-protege-error-label";


    private final ProjectId projectId;

    private final ExpandingTextBox editor;

    private final FlowPanel errorLabel = new FlowPanel();

    private final PrimitiveDataEditorSuggestOracle entitySuggestOracle;

    private final LanguageEditor languageEditor;


    private final Set<PrimitiveType> allowedTypes = new LinkedHashSet<PrimitiveType>();


    private Optional<OWLPrimitiveData> currentData = Optional.absent();

    private boolean showLinkForEntities = true;

    private FreshEntitiesHandler freshEntitiesHandler = new NullFreshEntitiesHandler();

    private String lastIconInsetStyleName = "empty-icon-inset";

    private PrimitiveDataParser primitiveDataParser = new DefaultPrimitiveDataParser();

    private boolean dirty = false;




    public DefaultPrimitiveDataEditor(ProjectId projectId) {
        this.projectId = projectId;
        this.languageEditor = new DefaultLanguageEditor();
        entitySuggestOracle = new PrimitiveDataEditorSuggestOracle(new EntitySuggestOracle(projectId, SUGGEST_LIMIT, EntityType.OBJECT_PROPERTY));
        TextBox textBox = new TextBox();
        textBox.setWidth("100%");
        textBox.setEnabled(false);

        editor = new ExpandingTextBox();
        editor.addStyleName("web-protege-form-layout-editor-input");
        add(editor);


        editor.setMode(ExpandingTextBoxMode.SINGLE_LINE);
        editor.setOracle(entitySuggestOracle);
        editor.addSelectionHandler(new SelectionHandler<SuggestOracle.Suggestion>() {
            @Override
            public void onSelection(SelectionEvent<SuggestOracle.Suggestion> event) {
                EntitySuggestion suggestion = (EntitySuggestion) event.getSelectedItem();
                setCurrentData(Optional.<OWLPrimitiveData>of(suggestion.getEntity()), EventStrategy.FIRE_EVENTS);
            }
        });
        editor.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> event) {
                handleEdit();
            }
        });


        languageEditor.addValueChangeHandler(new ValueChangeHandler<Optional<String>>() {
            @Override
            public void onValueChange(ValueChangeEvent<Optional<String>> event) {
                handleLanguageChanged();
            }
        });
        errorLabel.addStyleName(ERROR_STYLE_NAME);
        editor.setAnchorVisible(false);
        editor.addAnchorClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                handleAnchorClick(event);
            }
        });
    }

    public void setSuggestMode(PrimitiveDataEditorSuggestOracleMode mode) {
        entitySuggestOracle.setMode(mode);
    }


    @Override
    public Widget getWidget() {
        return this;
    }

    private void handleEdit() {
        reparsePrimitiveData();
        dirty = true;
    }

    @Override
    public void setFreshEntitiesHandler(FreshEntitiesHandler handler) {
        checkNotNull(handler);
        this.freshEntitiesHandler = handler;
    }

    public void setMode(ExpandingTextBoxMode mode) {
        checkNotNull(mode);
        editor.setMode(mode);
    }

    @Override
    public void setShowLinkForEntities(boolean showLinkForEntities) {
        this.showLinkForEntities = showLinkForEntities;
    }

    private void handleAnchorClick(ClickEvent event) {
        if (!currentData.isPresent()) {
            return;
        }

        EntityData entityData = currentData.get().accept(new OWLPrimitiveDataVisitorAdapter<EntityData, RuntimeException>() {
            @Override
            public EntityData visit(OWLClassData data) throws RuntimeException {
                final EntityData entityData = new EntityData(data.getEntity().getIRI().toString(), data.getBrowserText());
                entityData.setValueType(ValueType.Cls);
                return entityData;
            }

            @Override
            public EntityData visit(OWLObjectPropertyData data) throws RuntimeException {
                PropertyEntityData entityData = new PropertyEntityData(data.getEntity().getIRI().toString(), data.getBrowserText(), Collections.<EntityData>emptySet());
                entityData.setValueType(ValueType.Property);
                entityData.setPropertyType(PropertyType.OBJECT);
                return entityData;
            }

            @Override
            public EntityData visit(OWLDataPropertyData data) throws RuntimeException {
                PropertyEntityData entityData = new PropertyEntityData(data.getEntity().getIRI().toString(), data.getBrowserText(), Collections.<EntityData>emptySet());
                entityData.setValueType(ValueType.Property);
                entityData.setPropertyType(PropertyType.DATATYPE);
                return entityData;
            }

            @Override
            public EntityData visit(OWLAnnotationPropertyData data) throws RuntimeException {
                PropertyEntityData entityData = new PropertyEntityData(data.getEntity().getIRI().toString(), data.getBrowserText(), Collections.<EntityData>emptySet());
                entityData.setValueType(ValueType.Property);
                entityData.setPropertyType(PropertyType.ANNOTATION);
                return entityData;
            }

            @Override
            public EntityData visit(OWLNamedIndividualData data) throws RuntimeException {
                final EntityData entityData = new EntityData(data.getEntity().getIRI().toString(), data.getBrowserText());
                entityData.setValueType(ValueType.Instance);
                return entityData;
            }

            @Override
            public EntityData visit(OWLDatatypeData data) throws RuntimeException {
                return null;
            }

            @Override
            public EntityData visit(IRIData data) throws RuntimeException {
                Window.open(data.getObject().toString(), data.getBrowserText(), "");
                return null;
            }
        });



//        // TODO: Horrible HACK
//        if (entityData != null) {
//            Collection<EntityData> sel = new HashSet<EntityData>();
//            sel.add(entityData);
//            Widget parent = getParent();
//            while(parent != null) {
//                if(parent instanceof AbstractTab) {
//                    ((AbstractTab) parent).setSelection(sel);
//                    break;
//                }
//                parent = parent.getParent();
//            }
//            GWT.log("NAV to " + entityData);
//        }
//


    }

    /**
     * Returns true if the widget is enabled, false if not.
     */
    @Override
    public boolean isEnabled() {
        return editor.isEnabled();
    }

    /**
     * Sets whether this widget is enabled.
     * @param enabled <code>true</code> to enable the widget, <code>false</code>
     * to disable it
     */
    @Override
    public void setEnabled(boolean enabled) {
        editor.setEnabled(enabled);
        languageEditor.setEnabled(enabled);
    }

    public LanguageEditor getLanguageEditor() {
        return languageEditor;
    }


    private void showErrorLabel() {
        setupErrorLabel();
        add(errorLabel);
    }

    private void hideErrorLabel() {
        remove(errorLabel);
    }


    private void reparsePrimitiveData() {
        if(isCurrentDataRendered()) {
            return;
        }
        PrimitiveDataParsingContext context = new PrimitiveDataParsingContext(projectId, allowedTypes, freshEntitiesHandler);
        primitiveDataParser.parsePrimitiveData(editor.getText(), languageEditor.getValue(), context, new PrimitiveDataParserCallback() {
            @Override
            public void parsingFailure() {
                setCurrentData(Optional.<OWLPrimitiveData>absent(), EventStrategy.FIRE_EVENTS);
                showErrorLabel();
            }

            @Override
            public void onSuccess(Optional<OWLPrimitiveData> result) {
                hideErrorLabel();
                setCurrentData(result, EventStrategy.FIRE_EVENTS);
            }
        });
    }

    private boolean isCurrentDataRendered() {
        if(!currentData.isPresent()) {
            return editor.getText().isEmpty() && !languageEditor.getValue().isPresent();
        }
        OWLPrimitiveData data = currentData.get();
        String currentBrowserText = data.getBrowserText();
        if(!currentBrowserText.equals(editor.getText())) {
            return false;
        }
        if(!isCurrentEntityTypeAllowed()) {
            return false;
        }
        if(data instanceof OWLLiteralData) {
            final OWLLiteral literal = ((OWLLiteralData) data).getLiteral();
            Optional<String> lang;
            if(literal.hasLang()) {
                lang = Optional.of(literal.getLang());
            }
            else {
                lang = Optional.absent();
            }
            if(!lang.equals(languageEditor.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setPlaceholder(String placeholder) {
        editor.setPlaceholder(placeholder);
    }

    public void setDefaultPlaceholder() {
        StringBuilder sb = new StringBuilder();
        sb.append("Enter ");
        if (isClassesAllowed()) {
            sb.append("class name, ");
        }
        if (isObjectPropertiesAllowed()) {
            sb.append("object property name, ");
        }

    }

    @Override
    public String getPlaceholder() {
        return editor.getPlaceholder();
    }



    private void updateDisplayForCurrentData() {
        setIconInsetStyleNameForEntityData();
        validateCurrentEntityTypeAgainstAllowedTypes();


//        if (showLinkForEntities) {
//            editor.setAnchorVisible(true);
            if(isExternalIRI()) {
                editor.setAnchorTitle("Open link in new window");
                editor.setAnchorVisible(true);
            }
            else {
//                editor.setAnchorTitle("Navigate to " + currentData.get().getBrowserText());
                editor.setAnchorVisible(false);
            }
//        }
    }

    private boolean isExternalIRI() {
        if(!currentData.isPresent()) {
            return false;
        }
        OWLPrimitiveData data = currentData.get();
        if(!(data instanceof IRIData)) {
            return false;
        }
        IRI iri = (IRI) data.getObject();
        if(!iri.isAbsolute()) {
            return false;
        }
        if(!"http".equalsIgnoreCase(iri.getScheme())) {
            return false;
        }
        return true;
    }


    private void setIconInsetStyleName(String name) {
        if (lastIconInsetStyleName != null) {
            editor.getSuggestBox().removeStyleName(lastIconInsetStyleName);
        }
        lastIconInsetStyleName = name;
        editor.getSuggestBox().addStyleName(name);
    }

    private void setIconInsetStyleNameForEntityData() {
        if(!currentData.isPresent()) {
            clearIconInset();
            return;
        }
        final OWLPrimitiveData entityData = currentData.get();
        String styleName = entityData.accept(new OWLPrimitiveDataVisitorAdapter<String, RuntimeException>() {
            @Override
            protected String getDefaultReturnValue() {
                editor.setTitle("");
                return "empty-icon-inset";
            }

            @Override
            public String visit(OWLClassData data) throws RuntimeException {
                editor.setTitle(entityData.getBrowserText() + " is an owl:Class");
                return "class-icon-inset";
            }

            @Override
            public String visit(OWLObjectPropertyData data) throws RuntimeException {
                editor.setTitle(entityData.getBrowserText() + " is an owl:ObjectProperty");
                return "object-property-icon-inset";
            }

            @Override
            public String visit(OWLDataPropertyData data) throws RuntimeException {
                editor.setTitle(entityData.getBrowserText() + " is an owl:DataProperty");
                return "data-property-icon-inset";
            }

            @Override
            public String visit(OWLAnnotationPropertyData data) throws RuntimeException {
                editor.setTitle(entityData.getBrowserText() + " is an owl:AnnotationProperty");
                return "annotation-property-icon-inset";
            }

            @Override
            public String visit(OWLNamedIndividualData data) throws RuntimeException {
                editor.setTitle(entityData.getBrowserText() + " is an owl:NamedIndividual");
                return "individual-icon-inset";
            }

            @Override
            public String visit(OWLDatatypeData data) throws RuntimeException {
                editor.setTitle(entityData.getBrowserText() + " is an owl:Datatype");
                return "datatype-icon-inset";
            }

            @Override
            public String visit(OWLLiteralData data) throws RuntimeException {
                String styleName = "literal-icon-inset";
                OWLDatatype datatype = data.getLiteral().getDatatype();
                if (datatype.isBuiltIn()) {
                    OWL2Datatype owl2Datatype = datatype.getBuiltInDatatype();
                    if (owl2Datatype.isNumeric()) {
                        styleName = "numeric-literal-icon-inset";
                    }
                    else if (owl2Datatype.equals(OWL2Datatype.XSD_DATE_TIME)) {
                        styleName = "date-time-icon-inset";
                    }
                }
                String datatypeName = datatype.getIRI().getFragment();
                if (datatypeName == null) {
                    datatypeName = datatype.getIRI().toString();
                }
                StringBuilder tooltip = new StringBuilder();
                tooltip.append(entityData.getBrowserText());
                char c = datatypeName.charAt(0);
                if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') {
                    tooltip.append(" is an ");
                }
                else {
                    tooltip.append(" is a ");
                }
                tooltip.append(datatypeName);
                editor.setTitle(tooltip.toString());
                return styleName;
            }

            @Override
            public String visit(IRIData data) throws RuntimeException {
                if (data.isHTTPLink()) {
                    return "link-icon-inset";
                }
                else {
                    return "iri-icon-inset";
                }
            }
        });
        setIconInsetStyleName(styleName);
    }


    /**
     * Checks that the current entity type is one of the allowed types.
     */
    private void validateCurrentEntityTypeAgainstAllowedTypes() {
        if(!currentData.isPresent()) {
            hideErrorLabel();
            // Allowed to be empty
            return;
        }

        if(isCurrentEntityTypeAllowed()) {
            hideErrorLabel();
            return;
        }

        showErrorLabel();

    }

    private boolean isCurrentEntityTypeAllowed() {
        return !currentData.isPresent() || allowedTypes.contains(currentData.get().getType());
    }


//
    private void clearIconInset() {
        setIconInsetStyleName("empty-icon-inset");
    }

    protected void handleLanguageChanged() {
        reparsePrimitiveData();
    }


    /**
     * Gets this object's text.
     * @return the object's text
     */
//    @Override
    public String getText() {
        return editor.getText();
    }

    /**
     * Adds a {@link com.google.gwt.event.dom.client.FocusEvent} handler.
     * @param handler the focus handler
     * @return {@link com.google.gwt.event.shared.HandlerRegistration} used to remove this handler
     */
    @Override
    public HandlerRegistration addFocusHandler(FocusHandler handler) {
        return editor.addFocusHandler(handler);
    }

    /**
     * Adds a {@link com.google.gwt.event.dom.client.KeyUpEvent} handler.
     * @param handler the key up handler
     * @return {@link com.google.gwt.event.shared.HandlerRegistration} used to remove this handler
     */
    @Override
    public HandlerRegistration addKeyUpHandler(KeyUpHandler handler) {
        return editor.addKeyUpHandler(handler);
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public boolean isAnnotationPropertiesAllowed() {
        return allowedTypes.contains(PrimitiveType.ANNOTATION_PROPERTY);
    }

    public void setAnnotationPropertiesAllowed(boolean annotationPropertiesAllowed) {
        setAllowedType(PrimitiveType.ANNOTATION_PROPERTY, annotationPropertiesAllowed);
    }

    public boolean isDataPropertiesAllowed() {
        return allowedTypes.contains(PrimitiveType.DATA_PROPERTY);
    }

    public void setDataPropertiesAllowed(boolean dataPropertiesAllowed) {
        setAllowedType(PrimitiveType.DATA_PROPERTY, dataPropertiesAllowed);
    }

    public boolean isObjectPropertiesAllowed() {
        return allowedTypes.contains(PrimitiveType.OBJECT_PROPERTY);
    }

    public void setObjectPropertiesAllowed(boolean objectPropertiesAllowed) {
        setAllowedType(PrimitiveType.OBJECT_PROPERTY, objectPropertiesAllowed);
    }

    public boolean isClassesAllowed() {
        return allowedTypes.contains(PrimitiveType.CLASS);
    }

    public void setClassesAllowed(boolean classesAllowed) {
        setAllowedType(PrimitiveType.CLASS, classesAllowed);
    }

    public boolean isDatatypesAllowed() {
        return allowedTypes.contains(PrimitiveType.DATA_TYPE);
    }

    public void setDatatypesAllowed(boolean datatypesAllowed) {
        setAllowedType(PrimitiveType.DATA_TYPE, datatypesAllowed);
    }

    public boolean isNamedIndividualsAllowed() {
        return allowedTypes.contains(PrimitiveType.NAMED_INDIVIDUAL);
    }

    public void setNamedIndividualsAllowed(boolean namedIndividualsAllowed) {
        setAllowedType(PrimitiveType.NAMED_INDIVIDUAL, namedIndividualsAllowed);
    }

    public boolean isLiteralAllowed() {
        return allowedTypes.contains(PrimitiveType.LITERAL);
    }

    public void setLiteralAllowed(boolean literalAllowed) {
        setAllowedType(PrimitiveType.LITERAL, literalAllowed);
    }

    public boolean isIRIAllowed() {
        return allowedTypes.contains(PrimitiveType.IRI);
    }

    public void setIRIAllowed(boolean iriAllowed) {
        setAllowedType(PrimitiveType.IRI, iriAllowed);
    }

    public void setAllowedType(PrimitiveType type, boolean allowed) {
        boolean revalidate;
        if (allowed) {
            revalidate = allowedTypes.add(type);
        }
        else {
            revalidate = allowedTypes.remove(type);
        }
        if (revalidate) {
            if (type.getEntityType() != null) {
                updateOracle();
            }
            validateCurrentEntityTypeAgainstAllowedTypes();
        }
    }

    private void updateOracle() {
        Set<EntityType<?>> types = getMatchTypes();
        entitySuggestOracle.setEntityTypes(types);
    }

    private Set<EntityType<?>> getMatchTypes() {
        Set<EntityType<?>> types = new LinkedHashSet<EntityType<?>>();
        for (PrimitiveType primitiveType : allowedTypes) {
            EntityType<?> entityType = primitiveType.getEntityType();
            if (entityType != null) {
                types.add(entityType);
            }
        }
        return types;
    }


    private void setupErrorLabel() {
        errorLabel.clear();
        final String text = editor.getText().trim();
        final HTML errorMessageLabel = new HTML(freshEntitiesHandler.getErrorMessage(text));
        errorLabel.add(errorMessageLabel);
        errorLabel.removeStyleName(ERROR_STYLE_NAME);
        errorLabel.addStyleName("web-protege-warning-label");
        if (freshEntitiesHandler.getFreshEntitiesPolicy() == FreshEntitiesPolicy.ALLOWED) {
            for (PrimitiveType primitiveType : allowedTypes) {
                final EntityType<?> entityType = primitiveType.getEntityType();
                if (entityType != null) {
                    Anchor anchor = new Anchor("Add as " + entityType.getName());
                    errorLabel.add(new SimplePanel(anchor));
                    anchor.addClickHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                            coerceToEntityType(entityType);
                        }
                    });
                }
            }
        }
    }

    @Override
    public void coerceToEntityType(EntityType<?> entityType) {
        String text = editor.getText();
        OWLEntity entity = freshEntitiesHandler.getFreshEntity(text, entityType);
        OWLPrimitiveData coercedData = DataFactory.getOWLEntityData(entity, text);
        setCurrentData(Optional.of(coercedData), EventStrategy.FIRE_EVENTS);
        updateDisplayForCurrentData();
    }

    ////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Adds a {@link com.google.gwt.event.logical.shared.ValueChangeEvent} handler.
     * @param handler the handler
     * @return the registration for the event
     */
    @Override
    public HandlerRegistration addValueChangeHandler(ValueChangeHandler<Optional<OWLPrimitiveData>> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    private void fireValueChangedEvent() {
        ValueChangeEvent.fire(this, getValue());
    }

    @Override
    public void setValue(OWLPrimitiveData object) {
        checkNotNull(object);
        setCurrentData(Optional.of(object), EventStrategy.DO_NOT_FIRE_EVENTS);
    }

    @Override
    public Optional<OWLPrimitiveData> getValue() {
        return currentData;
    }

    @Override
    public void clearValue() {
        setCurrentData(Optional.<OWLPrimitiveData>absent(), EventStrategy.DO_NOT_FIRE_EVENTS);
    }

    @Override
    public boolean isWellFormed() {
        return currentData.isPresent();
    }

    /**
     * Determines if this object is dirty.
     * @return {@code true} if the object is dirty, otherwise {@code false}.
     */
    @Override
    public boolean isDirty() {
        return dirty;
    }


    private void setCurrentData(Optional<OWLPrimitiveData> nextCurrentData, EventStrategy eventStrategy) {
        checkNotNull(nextCurrentData);
        dirty = false;
        if(currentData.equals(nextCurrentData)) {
            return;
        }
        currentData = nextCurrentData;
        if(nextCurrentData.isPresent()) {
            OWLPrimitiveData data = nextCurrentData.get();
            editor.setText(data.getBrowserText());
            if(data instanceof OWLLiteralData) {
                String lang = ((OWLLiteralData) data).getLiteral().getLang();
                languageEditor.setValue(lang);
            }
        }
        else {
            editor.setText("");
            languageEditor.setValue("");
        }
        updateDisplayForCurrentData();
        if (eventStrategy == EventStrategy.FIRE_EVENTS) {
            fireValueChangedEvent();
        }
    }

        @Override
    public void setAllowedTypes(SortedSet<PrimitiveType> primitiveTypes) {
        if (primitiveTypes.equals(this.allowedTypes)) {
            return;
        }
        this.allowedTypes.clear();
        this.allowedTypes.addAll(primitiveTypes);
        if (!allowedTypes.contains(PrimitiveType.LITERAL)) {
            setMode(ExpandingTextBoxMode.SINGLE_LINE);
        }
        else {
            setMode(ExpandingTextBoxMode.MULTI_LINE);
        }
        updateOracle();
        if (!isCurrentEntityTypeAllowed()) {
            reparsePrimitiveData();
        }
    }

    @Override
    public HandlerRegistration addDirtyChangedHandler(DirtyChangedHandler handler) {
        return addHandler(handler, DirtyChangedEvent.TYPE);
    }
}