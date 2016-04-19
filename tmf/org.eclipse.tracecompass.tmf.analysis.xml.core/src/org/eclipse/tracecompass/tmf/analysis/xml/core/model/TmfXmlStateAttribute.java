/*******************************************************************************
 * Copyright (c) 2014, 2015 Ecole Polytechnique de Montreal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Florian Wininger - Initial API and implementation
 ******************************************************************************/

package org.eclipse.tracecompass.tmf.analysis.xml.core.model;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import static org.eclipse.tracecompass.common.core.NonNullUtils.nullToEmptyString;
import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.XmlUtils;
import org.eclipse.tracecompass.tmf.analysis.xml.core.stateprovider.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.w3c.dom.Element;

/**
 * This Class implements a single attribute value in the XML-defined state
 * system.
 *
 * <pre>
 * Examples:
 * <stateAttribute type="constant" value="Threads" />
 * <stateAttribute type="query" />
 *      <stateAttribute type="constant" value="CPUs" />
 *      <stateAttribute type="eventField" value="cpu" />
 *      <stateAttribute type="constant" value="Current_thread" />
 * </attribute>
 * </pre>
 *
 * @author Florian Wininger
 */
public abstract class TmfXmlStateAttribute implements ITmfXmlStateAttribute {

    private enum StateAttributeType {
        NONE,
        CONSTANT,
        EVENTFIELD,
        QUERY,
        LOCATION,
        SELF,
        EVENTNAME
    }

    private final String CURRENT_STATE = "#currentState"; //$NON-NLS-1$

    private final String CURRENT_SCENARIO = "#CurrentScenario"; //$NON-NLS-1$

    /** Type of attribute */
    private final StateAttributeType fType;

    /** Attribute's name */
    private final @Nullable String fName;

    /** List of attributes for a query */
    private final List<ITmfXmlStateAttribute> fQueryList = new LinkedList<>();

    private final IXmlStateSystemContainer fContainer;

    /**
     * Constructor
     *
     * @param modelFactory
     *            The factory used to create XML model elements
     * @param attribute
     *            XML element of the attribute
     * @param container
     *            The state system container this state attribute belongs to
     */
    protected TmfXmlStateAttribute(ITmfXmlModelFactory modelFactory, Element attribute, IXmlStateSystemContainer container) {
        fContainer = container;

        switch (attribute.getAttribute(TmfXmlStrings.TYPE)) {
        case TmfXmlStrings.TYPE_CONSTANT:
            fType = StateAttributeType.CONSTANT;
            fName = fContainer.getAttributeValue(attribute.getAttribute(TmfXmlStrings.VALUE));
            break;
        case TmfXmlStrings.EVENT_FIELD:
            fType = StateAttributeType.EVENTFIELD;
            fName = fContainer.getAttributeValue(attribute.getAttribute(TmfXmlStrings.VALUE));
            break;
        case TmfXmlStrings.TYPE_LOCATION:
            fType = StateAttributeType.LOCATION;
            fName = fContainer.getAttributeValue(attribute.getAttribute(TmfXmlStrings.VALUE));
            break;
        case TmfXmlStrings.TYPE_QUERY:
            List<@Nullable Element> childElements = XmlUtils.getChildElements(attribute);
            for (Element subAttributeNode : childElements) {
                if (subAttributeNode == null) {
                    continue;
                }
                ITmfXmlStateAttribute subAttribute = modelFactory.createStateAttribute(subAttributeNode, fContainer);
                fQueryList.add(subAttribute);
            }
            fType = StateAttributeType.QUERY;
            fName = null;
            break;
        case TmfXmlStrings.TYPE_EVENT_NAME:
            fType = StateAttributeType.EVENTNAME;
            fName = fContainer.getAttributeValue(attribute.getAttribute(TmfXmlStrings.VALUE));
            break;
        case TmfXmlStrings.NULL:
            fType = StateAttributeType.NONE;
            fName = null;
            break;
        case TmfXmlStrings.TYPE_SELF:
            fType = StateAttributeType.SELF;
            fName = null;
            break;
        default:
            throw new IllegalArgumentException("TmfXmlStateAttribute constructor: The XML element is not of the right type"); //$NON-NLS-1$
        }
    }

    /**
     * @since 2.0
     */
    @Override
    public int getAttributeQuark(int startQuark, @Nullable TmfXmlScenarioInfo scenarioInfo) {
        return getAttributeQuark(null, startQuark, scenarioInfo);
    }

    /**
     * Basic quark-retrieving method. Pass an attribute in parameter as an array
     * of strings, the matching quark will be returned. If the attribute does
     * not exist, it will add the quark to the state system if the context
     * allows it.
     *
     * See {@link ITmfStateSystemBuilder#getQuarkAbsoluteAndAdd(String...)}
     *
     * @param path
     *            Full path to the attribute
     * @return The quark for this attribute
     * @throws AttributeNotFoundException
     *             The attribute does not exist and cannot be added
     */
    protected abstract int getQuarkAbsoluteAndAdd(String... path) throws AttributeNotFoundException;

    /**
     * Quark-retrieving method, but the attribute is queried starting from the
     * startNodeQuark. If the attribute does not exist, it will add it to the
     * state system if the context allows it.
     *
     * See {@link ITmfStateSystemBuilder#getQuarkRelativeAndAdd(int, String...)}
     *
     * @param startNodeQuark
     *            The quark of the attribute from which 'path' originates.
     * @param path
     *            Relative path to the attribute
     * @return The quark for this attribute
     * @throws AttributeNotFoundException
     *             The attribute does not exist and cannot be added
     */
    protected abstract int getQuarkRelativeAndAdd(int startNodeQuark, String... path) throws AttributeNotFoundException;

    /**
     * Get the state system associated with this attribute's container
     *
     * @return The state system associated with this state attribute
     */
    protected @Nullable ITmfStateSystem getStateSystem() {
        return fContainer.getStateSystem();
    }

    /**
     * @since 2.0
     */
    @Override
    public int getAttributeQuark(@Nullable ITmfEvent event, int startQuark, @Nullable TmfXmlScenarioInfo scenarioInfo) {
        ITmfStateSystem ss = getStateSystem();
        if (ss == null) {
            throw new IllegalStateException("The state system hasn't been initialized yet"); //$NON-NLS-1$
        }
        String name = nullToEmptyString(fName);
        if (name.length() > 0 && name.charAt(0) == '#' && scenarioInfo == null) {
            throw new IllegalStateException("XML Attribute needs " + fName + " but the data is not available.");  //$NON-NLS-1$//$NON-NLS-2$
        }
        name = name.equals(CURRENT_STATE) ? checkNotNull(scenarioInfo).getActiveState()
                        : fName;

        try {
            switch (fType) {
            case CONSTANT: {
                int quark;
                if (name == null) {
                    throw new IllegalStateException("Invalid attribute name"); //$NON-NLS-1$
                }
                if (name.equals(CURRENT_SCENARIO)) {
                    return checkNotNull(scenarioInfo).getQuark();
                }
                if (startQuark == IXmlStateSystemContainer.ROOT_QUARK) {
                    quark = getQuarkAbsoluteAndAdd(name);
                } else {
                    quark = getQuarkRelativeAndAdd(startQuark, name);
                }
                return quark;
            }
            case EVENTFIELD: {
                int quark = IXmlStateSystemContainer.ERROR_QUARK;
                if (event == null) {
                    Activator.logWarning("XML State attribute: looking for an event field, but event is null"); //$NON-NLS-1$
                    return quark;
                }
                if (name == null) {
                    throw new IllegalStateException("Invalid attribute name"); //$NON-NLS-1$
                }
                if (name.equals(TmfXmlStrings.CPU)) {
                    /* See if the event advertises a CPU aspect */
                    Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(
                            event.getTrace(), TmfCpuAspect.class, event);
                    if (cpu != null) {
                        quark = getQuarkRelativeAndAdd(startQuark, cpu.toString());
                    }
                } else {
                    final ITmfEventField content = event.getContent();
                    /* stop if the event field doesn't exist */
                    if (content.getField(name) == null) {
                        return IXmlStateSystemContainer.ERROR_QUARK;
                    }

                    Object field = content.getField(name).getValue();

                    if (field instanceof String) {
                        String fieldString = (String) field;
                        quark = getQuarkRelativeAndAdd(startQuark, fieldString);
                    } else if (field instanceof Long) {
                        Long fieldLong = (Long) field;
                        quark = getQuarkRelativeAndAdd(startQuark, fieldLong.toString());
                    } else if (field instanceof Integer) {
                        Integer fieldInterger = (Integer) field;
                        quark = getQuarkRelativeAndAdd(startQuark, fieldInterger.toString());
                    }
                }
                return quark;
            }
            case QUERY: {
                int quark;
                ITmfStateValue value = TmfStateValue.nullValue();
                int quarkQuery = IXmlStateSystemContainer.ROOT_QUARK;

                for (ITmfXmlStateAttribute attrib : fQueryList) {
                    quarkQuery = attrib.getAttributeQuark(event, quarkQuery, scenarioInfo);
                    if (quarkQuery == IXmlStateSystemContainer.ERROR_QUARK) {
                        break;
                    }
                }

                // the query may fail: for example CurrentThread if there
                // has not been a sched_switch event
                if (quarkQuery != IXmlStateSystemContainer.ERROR_QUARK) {
                    value = ss.queryOngoingState(quarkQuery);
                }

                switch (value.getType()) {
                case INTEGER: {
                    int result = value.unboxInt();
                    quark = getQuarkRelativeAndAdd(startQuark, String.valueOf(result));
                    break;
                }
                case LONG: {
                    long result = value.unboxLong();
                    quark = getQuarkRelativeAndAdd(startQuark, String.valueOf(result));
                    break;
                }
                case STRING: {
                    String result = value.unboxStr();
                    quark = getQuarkRelativeAndAdd(startQuark, result);
                    break;
                }
                case DOUBLE:
                case NULL:
                default:
                    quark = IXmlStateSystemContainer.ERROR_QUARK; // error
                    break;
                }
                return quark;
            }
            case LOCATION: {
                int quark = startQuark;
                String idLocation = name;

                /* TODO: Add a fContainer.getLocation(id) method */
                for (TmfXmlLocation location : fContainer.getLocations()) {
                    if (location.getId().equals(idLocation)) {
                        quark = location.getLocationQuark(event, quark, scenarioInfo);
                        if (quark == IXmlStateSystemContainer.ERROR_QUARK) {
                            break;
                        }
                    }
                }
                return quark;
            }
            case EVENTNAME: {
                int quark = IXmlStateSystemContainer.ERROR_QUARK;
                if (event == null) {
                    Activator.logWarning("XML State attribute: looking for an eventname, but event is null"); //$NON-NLS-1$
                    return quark;
                }
                quark = getQuarkRelativeAndAdd(startQuark, event.getName());
                return quark;
            }
            case SELF:
                return startQuark;
            case NONE:
            default:
                return startQuark;
            }
        } catch (AttributeNotFoundException ae) {
            /*
             * This can be happen before the creation of the node for a query in
             * the state system. Example : current thread before a sched_switch
             */
            return IXmlStateSystemContainer.ERROR_QUARK;
        } catch (StateValueTypeException e) {
            /*
             * This would happen if we were trying to push/pop attributes not of
             * type integer. Which, once again, should never happen.
             */
            Activator.logError("StateValueTypeException", e); //$NON-NLS-1$
            return IXmlStateSystemContainer.ERROR_QUARK;
        }
    }

    @Override
    public String toString() {
        return "TmfXmlStateAttribute " + fType + ": " + fName; //$NON-NLS-1$ //$NON-NLS-2$
    }

}
