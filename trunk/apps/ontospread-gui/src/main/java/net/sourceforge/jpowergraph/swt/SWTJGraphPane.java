package net.sourceforge.jpowergraph.swt;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import net.sourceforge.jpowergraph.Edge;
import net.sourceforge.jpowergraph.Graph;
import net.sourceforge.jpowergraph.GraphListener;
import net.sourceforge.jpowergraph.GroupLegendItem;
import net.sourceforge.jpowergraph.Legend;
import net.sourceforge.jpowergraph.NodeLegendItem;
import net.sourceforge.jpowergraph.Node;
import net.sourceforge.jpowergraph.SubGraphHighlighter;
import net.sourceforge.jpowergraph.defaults.DefaultSubGraphHighlighter;
import net.sourceforge.jpowergraph.lens.LegendLens;
import net.sourceforge.jpowergraph.lens.Lens;
import net.sourceforge.jpowergraph.lens.LensListener;
import net.sourceforge.jpowergraph.lens.LensSet;
import net.sourceforge.jpowergraph.manipulator.Manipulator;
import net.sourceforge.jpowergraph.painters.EdgePainter;
import net.sourceforge.jpowergraph.painters.LegendPainter;
import net.sourceforge.jpowergraph.painters.NodePainter;
import net.sourceforge.jpowergraph.painters.edge.ArrowEdgePainter;
import net.sourceforge.jpowergraph.painters.node.ShapeNodePainter;
import net.sourceforge.jpowergraph.pane.CursorChanger;
import net.sourceforge.jpowergraph.pane.JGraphPane;
import net.sourceforge.jpowergraph.pane.PopupDisplayer;
import net.sourceforge.jpowergraph.swt.manipulator.DefaultSWTContextMenuListener;
import net.sourceforge.jpowergraph.swt.manipulator.DefaultSWTToolTipListener;
import net.sourceforge.jpowergraph.swt.manipulator.SWTCursorChanger;
import net.sourceforge.jpowergraph.swt.manipulator.SWTManipulatorListener;
import net.sourceforge.jpowergraph.swt.manipulator.SWTPopupDisplayer;
import net.sourceforge.jpowergraph.swtswinginteraction.JPowerGraphGraphics;
import net.sourceforge.jpowergraph.swtswinginteraction.geometry.JPowerGraphDimension;
import net.sourceforge.jpowergraph.swtswinginteraction.geometry.JPowerGraphPoint;
import net.sourceforge.jpowergraph.swtswinginteraction.geometry.JPowerGraphRectangle;
import net.sourceforge.jpowergraph.swtswinginteraction.geometry.RectangleUtil;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

/**
 * The panel capable of visualizing the graph.
 */
public class SWTJGraphPane extends Canvas implements PaintListener, JGraphPane {
    private Graph m_graph;
    private Legend m_legend;
    private Lens m_lens;
    
    private GraphListener m_graphListner;
    private LensListener m_lensListener;
    
    private Map <Node, JPowerGraphPoint> m_nodePositions;
    private List <Manipulator> m_manipulators;
    private Map <String, Manipulator> m_manipulatorsByName;

    private Composite parent;

    private Color backgroundColor;

    private SubGraphHighlighter subGraphHighlighter;
    private NodePainter defaultNodePainter;
    private HashMap <Class, NodePainter> nodePainters;
    private EdgePainter defaultEdgePainter;
    private HashMap <Class, EdgePainter> edgePainters;
    private LegendPainter defaultLegendPainter;
    private int nodeSize = NodePainter.LARGE;
    
    private JPowerGraphGraphics tempGraphics;
    
    private boolean antialias = false;
    private PopupDisplayer popupDisplayer;
    private CursorChanger cursorChanger;
    private Map<Manipulator, SWTManipulatorListener> manipulatorListenerMap;
    private ColourManager colourManager;
    
    public SWTJGraphPane(Composite theParent, Graph graph) {
        this(theParent, graph, null);
    }
    
    /**
     * Creates a graph pane.
     * 
     * @param graph
     *            the graph
     */
    public SWTJGraphPane(Composite theParent, Graph graph, GroupLegendItem theLegendRoot) {
        super(theParent, SWT.NO_BACKGROUND);

        this.parent = theParent;

        m_legend = new Legend(theLegendRoot, true);
        m_graphListner = new GraphHandler();
        m_lensListener = new LensHandler();
        m_nodePositions = new HashMap <Node, JPowerGraphPoint>();
        m_manipulators = new ArrayList <Manipulator> ();
        m_manipulatorsByName = new HashMap <String, Manipulator> ();

        backgroundColor = parent.getDisplay().getSystemColor(SWT.COLOR_WHITE);
        subGraphHighlighter = new DefaultSubGraphHighlighter();
        defaultNodePainter = new ShapeNodePainter(ShapeNodePainter.RECTANGLE);
        nodePainters = new HashMap <Class, NodePainter> ();
        defaultEdgePainter = new ArrowEdgePainter();
        edgePainters = new HashMap <Class, EdgePainter> ();
        defaultLegendPainter = new LegendPainter();
        
        popupDisplayer = new SWTPopupDisplayer(getDisplay(), new DefaultSWTToolTipListener(), new DefaultSWTContextMenuListener(graph, new LensSet(), new Integer[]{}, new Integer[]{}));
        cursorChanger = new SWTCursorChanger(getDisplay()); 
        manipulatorListenerMap = new HashMap <Manipulator, SWTManipulatorListener> ();

        colourManager =  new ColourManager(getDisplay());
        
        setBackground(backgroundColor);
        refreshLegend(graph);
        setGraph(graph);
        
        this.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent arg0) {
                synchronized (SWTJGraphPane.this){
                    SWTJGraphPane.this.redraw();
                }
            }

            public void focusLost(FocusEvent arg0) {
                synchronized (SWTJGraphPane.this){
                    SWTJGraphPane.this.redraw();
                }
            }
        });
        
        this.addControlListener(new ControlListener() {
            public void controlMoved(ControlEvent arg0) {
                synchronized (SWTJGraphPane.this){
                    SWTJGraphPane.this.redraw();
                }
            }

            public void controlResized(ControlEvent arg0) {
                synchronized (SWTJGraphPane.this){
                    m_nodePositions.clear();
                    SWTJGraphPane.this.redraw();
                }
            }
        });
        
        this.addPaintListener(this);
    }

    /**
     * Adds a manipulator to this pane. If a manipulator with the same name if
     * registered, if is rist removed.
     * 
     * @param manipulator
     *            the manipulator
     */
    public void addManipulator(Manipulator manipulator) {
        removeManipulator(manipulator.getName());
        m_manipulators.add(manipulator);
        m_manipulatorsByName.put(manipulator.getName(), manipulator);
        manipulator.setGraphPane(this);
        
        SWTManipulatorListener manipulatorListener = new SWTManipulatorListener(manipulator);
        if (manipulatorListener != null){
            manipulatorListenerMap.put(manipulator, manipulatorListener);
            this.addMouseListener(manipulatorListener);
            this.addMouseMoveListener(manipulatorListener);
            this.addMouseTrackListener(manipulatorListener);
            this.addKeyListener(manipulatorListener);
            this.addFocusListener(manipulatorListener);
        }
    }

    /**
     * Returns a manipulator with given name.
     * 
     * @param name
     *            the name of the manpulator
     * @return the manipulator with given name (or <code>null</code> if the
     *         manipualtor with given name is not registered)
     */
    public Manipulator getManipulator(String name) {
        return m_manipulatorsByName.get(name);
    }

    /**
     * Removes a manipulator with given name.
     * 
     * @param name
     *            the name of the manpulator
     */
    public void removeManipulator(String name) {
        Manipulator manipulator = m_manipulatorsByName.remove(name);
        if (manipulator != null) {
            m_manipulators.remove(manipulator);
            manipulator.setGraphPane(null);
            
            SWTManipulatorListener manipulatorListener = manipulatorListenerMap.get(manipulator);
            if (manipulatorListener != null){
                manipulatorListenerMap.remove(manipulator);
                this.removeMouseListener(manipulatorListener); 
                this.removeMouseMoveListener(manipulatorListener);
                this.removeMouseTrackListener(manipulatorListener);
                this.removeKeyListener(manipulatorListener);
                this.removeFocusListener(manipulatorListener);
            }
        }
    }

    /**
     * Returns the current lens.
     * 
     * @return the current lens (may be <code>null</code>)
     */
    public Lens getLens() {
        return m_lens;
    }

    /**
     * Sets the new lens.
     * 
     * @param lens
     *            the new lens
     */
    public void setLens(Lens lens) {
        if (m_lens != null){
            m_lens.removeLensListener(m_lensListener);
        }
        m_lens = lens;
        if (m_lens != null){
            m_lens.addLensListener(m_lensListener);
        }
        m_nodePositions.clear();
        synchronized (SWTJGraphPane.this){
            SWTJGraphPane.this.redraw();
        }
    }

    /**
     * Returns the current graph.
     * 
     * @return the current graph
     */
    public Graph getGraph() {
        return m_graph;
    }

    /**
     * Sets the current graph.
     * 
     * @param graph
     *            the new graph
     */
    public void setGraph(Graph graph) {
        if (m_graph != null){
            m_graph.removeGraphListener(m_graphListner);
        }
        m_graph = graph;
        if (m_graph != null){
            m_graph.addGraphListener(m_graphListner);
        }
        m_nodePositions.clear();
        synchronized (SWTJGraphPane.this){
            SWTJGraphPane.this.redraw();
        }
    }

    public boolean isAntialias() {
        return antialias;
    }
    
    public void setAntialias(boolean antialias) {
        this.antialias = antialias;
    }
    
    private SWTJPowerGraphGraphics buffer = null;
    
    /**
     * Updates the component.
     * 
     * @param g
     *            the graphics
     */
    public void paintControl(PaintEvent e) {
        SWTJPowerGraphGraphics main = new SWTJPowerGraphGraphics(this, e.gc, colourManager);
        if (buffer == null || (buffer.getImage().getBounds().width != getWidth() || buffer.getImage().getBounds().height != getHeight())){
            if (buffer != null){
                buffer.dispose();
            }
            buffer = (SWTJPowerGraphGraphics) main.getSubJPowerGraphGraphics(new JPowerGraphDimension(getWidth(), getHeight()));
        }
        else{
            buffer.reset();
            buffer.fillRectangle(0, 0, getWidth(), getHeight());
        }
        buffer.setAntialias(antialias);
        
        JPowerGraphRectangle clipRectangle = buffer.getClipping();
        if (m_graph != null){
            synchronized (m_graph) {
                JPowerGraphRectangle bounds = new JPowerGraphRectangle(0, 0, 0, 0);
                Iterator iterator = m_graph.getVisibleEdges().iterator();
                while (iterator.hasNext()) {
                    Edge edge = (Edge) iterator.next();
                    getEdgeScreenBounds(edge, bounds);
                    if (clipRectangle.intersects(bounds)){
                        paintEdge(buffer, edge);
                    }
                }
                iterator = m_graph.getVisibleNodes().iterator();
                while (iterator.hasNext()) {
                    Node node = (Node) iterator.next();
                    getNodeScreenBounds(node, bounds);
                    if (clipRectangle.intersects(bounds)){
                        paintNode(buffer, node);
                    }
                }
                for (int i = 0; i < m_manipulators.size(); i++){
                    m_manipulators.get(i).paint(buffer);
                }
                paintLegend(buffer, m_legend);
            }
        }
        main.drawSubJPowerGraph(buffer, 0, 0);
    }

    /**
     * Paints the edge.
     * 
     * @param g
     *            the graphics
     * @param edge
     *            the edge
     */
    protected void paintEdge(JPowerGraphGraphics g, Edge edge) {
        EdgePainter edgePainter = getPainterForEdge(edge);
        edgePainter.paintEdge(this, g, edge, subGraphHighlighter);
    }

    /**
     * Returns the painter for the edge.
     * 
     * @param edge
     *            the edge
     * @return the painter for the edge
     */
    public EdgePainter getPainterForEdge(Edge edge) {
        if (edgePainters.containsKey(edge.getClass())){
            return edgePainters.get(edge.getClass());
        }
        return defaultEdgePainter;
    }

    /**
     * Paints the node.
     * 
     * @param g
     *            the graphics
     * @param node
     *            the node
     */
    protected void paintNode(JPowerGraphGraphics g, Node node) {
        NodePainter nodePainter = getPainterForNode(node);
        nodePainter.paintNode(this, g, node, nodeSize, subGraphHighlighter);
    }

    /**
     * Returns the painter for the node.
     * 
     * @param node
     *            the node
     * @return the painter for the node
     */
    public NodePainter getPainterForNode(Node node) {
        if (nodePainters.containsKey(node.getClass())){
            return nodePainters.get(node.getClass());
        }
        return defaultNodePainter;
    }
    protected void paintLegend(JPowerGraphGraphics g, Legend legend){
        boolean drawLegend = false;
        if (m_lens instanceof LegendLens){
            drawLegend = ((LegendLens) m_lens).isShowLegend();
        }
        else if (m_lens instanceof LensSet){
            drawLegend = true;
            List lenses = (((LensSet) m_lens)).getLensOfType(LegendLens.class);
            for (int i = 0; i < lenses.size(); i++){
                drawLegend = drawLegend && ((LegendLens) lenses.get(i)).isShowLegend();
            }
        }
        
        legend.clearActionMap();
        if (drawLegend){
            JPowerGraphRectangle r = getDefaultLegendPainter().paintLegend(this, g, legend);
            legend.setLocation(r);
        }
        else{
            legend.setLocation(new JPowerGraphRectangle(0, 0, 0, 0));
        }
    }
    
    public LegendPainter getDefaultLegendPainter(){
        return defaultLegendPainter;
    }
    /**
     * Converts a given screen point into a point on the graph.
     * 
     * @param point
     *            the sreen point
     * @param graphPoint
     *            the calculatedpoint in the graph
     */
    public void screenToGraphPoint(JPowerGraphPoint point, Point2D graphPoint) {
        graphPoint.setLocation(point.x - getSize().x / 2, point.y - getSize().y / 2);
        if (m_lens != null)
            m_lens.undoLens(this, graphPoint);
    }

    /**
     * Converts a given graph point into a point on the screen.
     * 
     * @param graphPoint
     *            the point in the graph
     * @param point
     *            the calculated screen point
     */
    public void graphToScreenPoint(final Point2D graphPoint, final JPowerGraphPoint point) {
        double oldX = graphPoint.getX();
        double oldY = graphPoint.getY();
        if (m_lens != null) {
            m_lens.applyLens(this, graphPoint);
        }

        point.x = (int) graphPoint.getX() + getWidth() / 2;
        point.y = (int) graphPoint.getY() + getHeight() / 2;
        graphPoint.setLocation(oldX, oldY);
    }

    public Legend getLegend(){
        return m_legend;
    }
    
    public Legend getLegendAtPoint(JPowerGraphPoint point){
        if (m_legend.getLocation().contains(point)){
            return m_legend;
        }
        return null;
    }
    
    /**
     * Returns the node at given point, or <code>null</code> if there is no
     * such node.
     * 
     * @param point
     *            the screen point
     * @return the node at given point (or <code>null</code> if there is no
     *         such node)
     */
    public Node getNodeAtPoint(JPowerGraphPoint point) {
        synchronized (m_graph) {
            List nodes = m_graph.getVisibleNodes();
            ListIterator iterator = nodes.listIterator(nodes.size());
            while (iterator.hasPrevious()) {
                Node node = (Node) iterator.previous();
                NodePainter nodePainter = getPainterForNode(node);
                if (nodePainter.isInNode(this, node, point, nodeSize, 1))
                    return node;
            }
            return null;
        }
    }

    /**
     * Returns the set of nodes in given rectangle.
     * 
     * @param rectangle
     *            the rectangle in which the nodes must be located
     * @return the set of nodes in the region
     */
    public Set <Node> getNodesInRectangle(JPowerGraphRectangle rectangle) {
        synchronized (m_graph) {
            Set <Node> nodesInRectangle = new HashSet <Node> ();
            JPowerGraphRectangle nodeRectangle = new JPowerGraphRectangle(0, 0, 0, 0);
            Iterator <Node> nodes = m_graph.getVisibleNodes().iterator();
            while (nodes.hasNext()) {
                Node node = nodes.next();
                getNodeScreenBounds(node, nodeRectangle);
                if (RectangleUtil.contains(rectangle, nodeRectangle)) {
                    nodesInRectangle.add(node);
                }
            }
            return nodesInRectangle;
        }
    }

    /**
     * Returns the edge at given point, or <code>null</code> if there is no
     * such edge.
     * 
     * @param point
     *            the screen point
     * @return the edge at given point (or <code>null</code> if there is no
     *         such edge)
     */
    public Edge getNearestEdge(JPowerGraphPoint point) {
        Edge nearestEdge = null;
        double minDistance = 4;
        synchronized (m_graph) {
            List edges = m_graph.getVisibleEdges();
            ListIterator iterator = edges.listIterator(edges.size());
            while (iterator.hasPrevious()) {
                Edge edge = (Edge) iterator.previous();
                EdgePainter edgePainter = getPainterForEdge(edge);
                double distance = edgePainter.screenDistanceFromEdge(this, edge, point);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestEdge = edge;
                }
            }
            return nearestEdge;
        }
    }
    
    /**
     * Returns the position of the node on the screen.
     * 
     * @param node
     *            the node whose on-screen position is required
     * @return the position of the node on screen
     */
    public JPowerGraphPoint getScreenPointForNode(Node node) {
        JPowerGraphPoint point = m_nodePositions.get(node);
        if (point == null) {
            point = new JPowerGraphPoint(0, 0);
            graphToScreenPoint(new Point2D.Double(node.getX(), node.getY()), point);
            m_nodePositions.put(node, point);
        }
        return point;
    }

    /**
     * Updates the map of screen positions of nodes.
     */
    protected void updateNodeScreenPositions() {
        synchronized (m_graph) {
            Point2D graphPoint = new Point2D.Double();
            Iterator nodes = m_graph.getVisibleNodes().iterator();
            while (nodes.hasNext()) {
                Node node = (Node) nodes.next();
                JPowerGraphPoint point = m_nodePositions.get(node);
                if (point == null) {
                    point = new JPowerGraphPoint(0, 0);
                    m_nodePositions.put(node, point);
                }
                graphPoint.setLocation(node.getX(), node.getY());
                graphToScreenPoint(graphPoint, point);
            }
        }
    }

    /**
     * Returns the screen bounds of given node.
     * 
     * @param node
     *            the node for which the bounds must be returned
     * @param nodeScreenRectangle
     *            the rectangle receiving the node's coordinates
     */
    public void getNodeScreenBounds(Node node, JPowerGraphRectangle nodeScreenRectangle) {
        NodePainter nodePainter = getPainterForNode(node);
        nodePainter.getNodeScreenBounds(this, node, nodeSize, 1.0, nodeScreenRectangle);
    }

    /**
     * Repaints the given node.
     * 
     * @param node
     *            the node that needs to be repainted
     */
    public void repaintNode(Node node) {
        JPowerGraphRectangle nodeScreenRectangle = new JPowerGraphRectangle(0, 0, 0, 0);
        getNodeScreenBounds(node, nodeScreenRectangle);
        synchronized (SWTJGraphPane.this){
            SWTJGraphPane.this.redraw();
        }
    }

    /**
     * Returns the screen bounds of given edge.
     * 
     * @param edge
     *            the edge for which the bounds must be returned
     * @param edgeScreenRectangle
     *            the rectangle receiving the edge's coordinates
     */
    public void getEdgeScreenBounds(Edge edge, JPowerGraphRectangle edgeScreenRectangle) {
        EdgePainter edgePainter = getPainterForEdge(edge);
        edgePainter.getEdgeScreenBounds(this, edge, edgeScreenRectangle);
    }

    /**
     * Repaints the given edge.
     * 
     * @param edge
     *            the edge that needs to be repainted
     */
    public void repaintEdge(Edge edge) {
        JPowerGraphRectangle edgeScreenRectangle = new JPowerGraphRectangle(0, 0, 0, 0);
        getEdgeScreenBounds(edge, edgeScreenRectangle);

        edgeScreenRectangle.x -= 5;
        edgeScreenRectangle.y -= 5;
        edgeScreenRectangle.width += 10;
        edgeScreenRectangle.height += 10;

        synchronized (SWTJGraphPane.this){
            SWTJGraphPane.this.redraw();
        }
    }
    
    public void refreshLegend(Graph graph) {
        m_legend.clear();
        for (Iterator i = graph.getAllNodes().iterator(); i.hasNext();) {
            Node node = (Node) i.next();
            NodePainter nodePainter = getPainterForNode(node);
            m_legend.add(new NodeLegendItem(node.getClass(), nodePainter, node.getNodeType()));
        }
    }
    
    /**
     * Returns the node painter for nodes of a type that does not 
     * have a specific node painter. 
     * 
     * @see setDefaultNodePainter(NodePainter theNodePainter)
     * @see setNodePainter(Class theClass, NodePainter theNodePainter)
     * @see getNodePainter(Class theClass)
     * 
     * @return NodePainter
     */
    public NodePainter getDefaultNodePainter() {
        return defaultNodePainter;
    }
    
    /**
     * Sets the node painter for nodes of a type that does not 
     * have a specific node painter. 
     * 
     * @see setNodePainter(Class theClass, NodePainter theNodePainter)
     * @see getNodePainter(Class theClass)
     * 
     * @param theNodePainter The new default NodePainter
     */
    public void setDefaultNodePainter(NodePainter theNodePainter) {
        this.defaultNodePainter = theNodePainter;
        refreshLegend(m_graph);
    }
    
    /**
     * Returns the node painter for nodes of the specified class. This
     * method will return null if no node painter is specified for this 
     * class.
     * 
     * @see setNodePainter(Class theClass, NodePainter theNodePainter)
     * 
     * @param theClass The Node class type
     * 
     * @return NodePainter
     */
    public NodePainter getNodePainter(Class theClass) {
        return nodePainters.get(theClass);
    }
    
    /**
     * Sets the node painter for nodes of the specified class. To clear the 
     * specified node painter specify null for the nodePainter
     * 
     * @see getNodePainter(Class theClass)
     * 
     * @param theClass The Node class type
     * @param theNodePainter The new NodePainter for the specified class type
     * 
     * @return NodePainter
     */
    public void setNodePainter(Class theClass, NodePainter theNodePainter) {
        nodePainters.put(theClass, theNodePainter);
        refreshLegend(m_graph);
    }
    
    /**
     * Returns the edge painter for edges of a type that does not 
     * have a specific edge painter. 
     * 
     * @see setDefaultEdgePainter(EdgePainter theEdgePainter)
     * @see setEdgePainter(Class theClass, EdgePainter theEdgePainter)
     * @see getEdgePainter(Class theClass)
     * 
     * @return EdgePainter
     */
    public EdgePainter getDefaultEdgePainter() {
        return defaultEdgePainter;
    }
    
    /**
     * Sets the edge painter for nodes of a type that does not 
     * have a specific edge painter. 
     * 
     * @see getDefaultEdgePainter();
     * @see setEdgePainter(Class theClass, EdgePainter theEdgePainter)
     * @see getEdgePainter(Class theClass)
     * 
     * @param theEdgePainter The new default EdgePainter
     */
    public void setDefaultEdgePainter(EdgePainter theEdgePainter) {
        this.defaultEdgePainter = theEdgePainter;
    }
    
    /**
     * Returns the edge painter for nodes of the specified class. This
     * method will return null if no edge painter is specified for this 
     * class.
     * 
     * @see setEdgePainter(Class theClass, EdgePainter theEdgePainter)
     * 
     * @param theClass The Edge class type
     * 
     * @return EdgePainter
     */
    public EdgePainter getEdgePainter(Class theClass) {
        return edgePainters.get(theClass);
    }
    
    /**
     * Sets the edge painter for nodes of the specified class. To clear the 
     * specified edge painter specify null for the edgePainter
     * 
     * @see getEdgePainter(Class theClass)
     * 
     * @param theClass The Edge class type
     * @param theEdgePainter The new EdgePainter for the specified class type
     * 
     * @return EdgePainter
     */
    public void setEdgePainter(Class theClass, EdgePainter theEdgePainter) {
        edgePainters.put(theClass, theEdgePainter);
    }

    public void setNodeSize(int theNodeSize) {
        this.nodeSize = theNodeSize;
    }
    
    public SubGraphHighlighter getSubGraphHighlighter(){
        return subGraphHighlighter;
    }
    
    /**
     * Overridden to notify the manipulators that the scroll position has
     * changed.
     * 
     * @param rectangle
     *            the rectangle
     */
    public void scroll(int destX, int destY, int x, int y, int width, int height, boolean all) {
        super.scroll(destX, destY, x, y, width, height, all);
        for (int i = 0; i < m_manipulators.size(); i++)
            m_manipulators.get(i).notifyGraphPaneScrolled();
    }

    public JPowerGraphRectangle getVisibleRectangle() {
        Rectangle r = getClientArea(); 
        return new JPowerGraphRectangle(r.x, r.y, r.width, r.height);
    }
    
    public void scrollRectToVisible(JPowerGraphPoint thePoint) {
        scrollRectToVisible(new JPowerGraphRectangle(thePoint.x, thePoint.y, 300, 300));
    }
    
    public void scrollRectToVisible(JPowerGraphRectangle rectangle) {
        Composite parent = getParent();
        while (parent != null){
            if (parent instanceof SWTJGraphScrollPane){
                ((SWTJGraphScrollPane) parent).scrollRectToVisible(new Rectangle(rectangle.x, rectangle.y, rectangle.width, rectangle.height));
                break;
            }
        }
        
        for (int i=0;i<m_manipulators.size();i++){
            m_manipulators.get(i).notifyGraphPaneScrolled();
        }
    }
    
    /**
     * The handler of graph events.
     */
    protected class GraphHandler implements GraphListener {
        public void graphLayoutUpdated(final Graph graph) {
            if (!parent.isDisposed() && !parent.getDisplay().isDisposed()){
                parent.getDisplay().asyncExec(new Runnable() {
                    public void run() {
                        if (!isDisposed()){
                            updateNodeScreenPositions();
                            synchronized (SWTJGraphPane.this){
                                SWTJGraphPane.this.redraw();
                            }
                        }
                    }
                });
            }
        }

        public void graphUpdated(final Graph graph) {
            if (!parent.isDisposed() && !parent.getDisplay().isDisposed()){
                parent.getDisplay().asyncExec(new Runnable() {
                    public void run() {
                        if (!isDisposed()){
                            synchronized (SWTJGraphPane.this){
                                SWTJGraphPane.this.redraw();
                            }
                        }
                    }
                });
            }
        }

        public void graphContentsChanged(final Graph graph) {
            if (!parent.isDisposed() && !parent.getDisplay().isDisposed()){
                parent.getDisplay().syncExec(new Runnable() {
                    public void run() {
                        if (!isDisposed()){
                            m_nodePositions.clear();
                            synchronized (SWTJGraphPane.this){
                                SWTJGraphPane.this.redraw();
                            }
                        }
                    }
                });
            }
        }

        public void elementsAdded(final Graph graph, final Collection nodes, final Collection edges) {
            if (!parent.isDisposed() && !parent.getDisplay().isDisposed()){
                parent.getDisplay().syncExec(new Runnable() {
                    public void run() {
                        if (!isDisposed()){
                            refreshLegend(graph);
                            synchronized (SWTJGraphPane.this){
                                SWTJGraphPane.this.redraw();
                            }
                        }
                    }
                });
            }
        }

        public void elementsRemoved(final Graph graph, final Collection nodes, final Collection edges) {
            if (!parent.isDisposed() && !parent.getDisplay().isDisposed()){
                parent.getDisplay().syncExec(new Runnable() {
                    public void run() {
                        if (!isDisposed()){
                            if (nodes != null) {
                                Iterator iterator = nodes.iterator();
                                while (iterator.hasNext()) {
                                    Node node = (Node) iterator.next();
                                    m_nodePositions.remove(node);
                                }
                            }
                            refreshLegend(graph);
                            synchronized (SWTJGraphPane.this){
                                SWTJGraphPane.this.redraw();
                            }
                        }
                    }
                });
            }
        }
    }

    /**
     * The handler of lens events.
     */
    protected class LensHandler implements LensListener {
        public void lensUpdated(Lens lens) {
            updateNodeScreenPositions();
            synchronized (SWTJGraphPane.this){
                SWTJGraphPane.this.redraw();
            }
        }
    }

    public int getWidth() {
        return getSize().x;
    }

    public int getHeight() {
        return getSize().y;
    }
    
    public JPowerGraphGraphics getJPowerGraphGraphics() {
        if (tempGraphics == null){
            tempGraphics = new SWTJPowerGraphGraphics(this, new Image(parent.getDisplay(), 100, 100), colourManager);
        }
        return tempGraphics;
    }

    public PopupDisplayer getPopupDisplayer() {
        return popupDisplayer;
    }

    public CursorChanger getCursorChanger() {
        return cursorChanger;
    }
    
    public void dispose() {
        super.dispose();
        m_nodePositions.clear();
        m_manipulators.clear();
        m_manipulatorsByName.clear();
        if (!backgroundColor.isDisposed()){
            backgroundColor.dispose();
        }
        if (!tempGraphics.isDisposed()){
            tempGraphics.dispose();
        }
        ((SWTPopupDisplayer) popupDisplayer).dispose();
        ((SWTCursorChanger) cursorChanger).dispose();
    }

    public JPowerGraphPoint getScreenLocation() {
        Point e = toDisplay(0, 0);
        return new JPowerGraphPoint(e.x, e.y);
    }

    public void setPopupDisplayer(PopupDisplayer thePopupDisplayer) {
        this.popupDisplayer = thePopupDisplayer;
    }
}
