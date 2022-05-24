/*
 * DOMUtil.java
 * Copyright (C) 2006 Nicholas Killewald
 * Portions Copyright (C) 2005 Patrick Niemeyer
 *
 * This file is distributed under the terms of the BSD license.
 * See the LICENSE file under the toplevel images/ directory for terms.
 */

package net.exclaimindustries.tools;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;


/**
 * These are the <code>DOMUtil</code> classes presented in Learning Java, 3rd Edition.
 * Most of them, anyway.  At any rate, they're all a bunch of tools for reading
 * XML from DOM objects.  For DOM tree navigation, however, you're on your own. 
 *
 * @author Nicholas Killewald, Patrick Niemeyer
 */
public class DOMUtil {
    
    /**
     * Get the first element with the given name as a child of the given
     * element.  A RuntimeException will be thrown if the named element doesn't
     * exist.
     *
     * @param element element to get a child from
     * @param name name of child
     * @return the element in question
     */
    public static Element getFirstElement(Element element, String name) {
        NodeList nl = element.getElementsByTagName(name);
        if(nl.getLength() < 1) throw new RuntimeException("Element: " + element + "does not contain: " + name);
        return (Element)nl.item(0);
    }
    
    /**
     * Specialized form of getSimpleElementText that takes the name of an
     * element to get the text from.  Note that this will only take the first
     * occurrence of the named element.
     *
     * @param node element to retrieve the text from
     * @param name named element as a child of <code>node</code> to retrieve the text from
     * @return the text in the named element
     * @see #getSimpleElementText(Element)
     */
    public static String getSimpleElementText(Element node, String name) {
        Element namedElement = getFirstElement(node, name);
        return getSimpleElementText(namedElement);
    }
    
    /**
     * Returns the text contained in a simple node.  A "simple node", in this
     * case, means only the text elements with no extra tags in between.  Any
     * tags like that will be ignored.  You'll need to navigate the tree
     * yourself to dig anything else out of it.
     *
     * @param node element to retrieve text from
     * @return the text in the element
     */
    public static String getSimpleElementText(Element node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();
        for(int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if(child instanceof Text) sb.append(child.getNodeValue());
        }
        return sb.toString();
    }
    
    /**
     * Returns a concatenation of all elements with the given name.  This is
     * just in case you don't feel just getting one is enough.  Paranoid.
     *
     * @param node the node to get the text from
     * @param name the name of the element to get the text from
     * @return all the text
     */
    public static String getAllElementText(Element node, String name) {
        StringBuilder temp = new StringBuilder();
        NodeList list = node.getElementsByTagName(name);
        for(int j = 0; j < list.getLength(); j++) {
            temp.append(DOMUtil.getSimpleElementText((Element)list.item(j)));
        }
        return temp.toString();
    }
    
    /**
     * Returns the value of the given attribute of the also-given node.
     *
     * @param node Element to find the attribute from
     * @param name attribute to get
     * @return the string in the specified element, or null if the attribute doesn't exist or if an error occurs
     */
    public static String getSimpleAttributeText(Element node, String name) {
        NamedNodeMap nnm = node.getAttributes();
        Node n = nnm.getNamedItem(name);
        try {
            return n.getNodeValue();
        } catch (Exception e) {
            return null;
        }
    }
    
}
