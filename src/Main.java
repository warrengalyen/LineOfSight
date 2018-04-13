package LineofSight;

import java.util.List;
import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Stage;

/**
 * Brute Force Line of Sight Algorithm: Use ScanLines to detect the visible area from a given position. 
 */
public class Main extends Application {

	Random rnd = new Random();
	
	Canvas backgroundCanvas;
	GraphicsContext backgroundGraphicsContext;

	Canvas foregroundCanvas;
	GraphicsContext foregroundGraphicsContext;

	/**
	 * Container for canvas and other nodes like attractors and repellers
	 */
	Pane layerPane;

	AnimationTimer animationLoop;

	Scene scene;

	List<Line> sceneLines;
	
	/**
	 * Current mouse location
	 */
	MouseStatus mouseStatus = new MouseStatus();
	
	LevelGenerator levelGenerator;
	
	Algorithm algorithm = new Algorithm();
	
	@Override
	public void start(Stage primaryStage) {

		BorderPane root = new BorderPane();

		// canvas
		backgroundCanvas = new Canvas(Settings.get().getCanvasWidth(), Settings.get().getCanvasHeight());
		backgroundGraphicsContext = backgroundCanvas.getGraphicsContext2D();

		foregroundCanvas = new Canvas(Settings.get().getCanvasWidth(), Settings.get().getCanvasHeight());
		foregroundGraphicsContext = foregroundCanvas.getGraphicsContext2D();
		
		// layers
		layerPane = new Pane();
		layerPane.getChildren().addAll(backgroundCanvas, foregroundCanvas);

		backgroundCanvas.widthProperty().bind(layerPane.widthProperty());
		foregroundCanvas.widthProperty().bind(layerPane.widthProperty());
		
		root.setCenter(layerPane);

		// toolbar
		Node toolbar = Settings.get().createToolbar();
		root.setRight(toolbar);
		
		scene = new Scene(root, Settings.get().getSceneWidth(), Settings.get().getSceneHeight(), Settings.get().getSceneColor());

		primaryStage.setScene(scene);
		primaryStage.setTitle("Demo");
		
//		primaryStage.setFullScreen(true);
//		primaryStage.setFullScreenExitHint("");
		primaryStage.show();

		
		// add content
		createObjects();

		// listeners for settings
		addSettingsListeners();
		
		// add mouse location listener
		addInputListeners();

		// add context menus
		addCanvasContextMenu( backgroundCanvas);
		
		// run animation loop
		startAnimation();

	}

	private void createObjects() {
		
		levelGenerator = new LevelGenerator();
		
		sceneLines = levelGenerator.getLines();
	}
	
	

	private void startAnimation() {

		// start game
		animationLoop = new AnimationTimer() {
			
			FpsCounter fpsCounter = new FpsCounter();
			
			@Override
			public void handle(long now) {

				// update fps
				// ----------------------------
				fpsCounter.update( now);

				// paint background canvas
				// ----------------------------
				
				// clear canvas. we don't use clearRect because we want a black background
				backgroundGraphicsContext.setFill( Settings.get().getBackgroundColor());
				backgroundGraphicsContext.fillRect(0, 0, backgroundCanvas.getWidth(), backgroundCanvas.getHeight());

				// background
				paintGrid( Settings.get().getGridColor());

				// paint foreground canvas
				// ----------------------------
				// draw depending on mouse button down
				paintOnCanvas();
				
				// update overlays (statistics)
				// ----------------------------

				// show fps and other debug info
				backgroundGraphicsContext.setFill(Color.BLACK);
				backgroundGraphicsContext.fillText( "Fps: " + fpsCounter.getFrameRate(), 1, 10);

			}
		};

		animationLoop.start();

	}
	
	
	private void paintOnCanvas()  {

		// clear canvas
		GraphicsContext gc = foregroundGraphicsContext;
		gc.clearRect(0, 0, foregroundCanvas.getWidth(), foregroundCanvas.getHeight());
		
		// scanlines
		List<Line> scanLines = algorithm.createScanLines( mouseStatus.x, mouseStatus.y);

		if( Settings.get().isDrawScanLines()) {

			gc.setStroke(Color.BLUE.deriveColor(1, 1, 1, 0.3));
			gc.setFill(Color.BLUE);
			
			for( Line line: scanLines) {
				drawLine(line);
			}
		}
		

		// environment
		if( Settings.get().isEnvironmentVisible()) {

			// room floor
			gc.setFill(Color.LIGHTGREY.deriveColor(1, 1, 1, 0.3));
			for( Bounds bounds: levelGenerator.getRoomDimensions()) {
				gc.fillRect(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
			}
			
			// scene lines
			gc.setStroke(Color.BLACK);
			gc.setFill(Color.BLACK);
	
			for( Line line: sceneLines) {
				drawLine(line);
			}
		}

		// intersections
		
		// get intersection points
		List<PVector> points = algorithm.getIntersectionPoints( scanLines, sceneLines);
		
		// draw intersection shape
		if( Settings.get().isDrawShape()) {

			gc.setStroke(Color.GREEN);

			if( Settings.get().isGradientShapeFill()) {
				
				Color LIGHT_GRADIENT_START = Color.YELLOW.deriveColor(1, 1, 1, 0.5);
				Color LIGHT_GRADIENT_END = Color.TRANSPARENT;
	
				// TODO: don't use the center of the shape; instead calculate the center depending on the user position
		        RadialGradient gradient = new RadialGradient(
		                0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
		                new Stop(0, LIGHT_GRADIENT_START),
		                new Stop(1, LIGHT_GRADIENT_END));
		                gc.setFill(gradient);
	
	   			gc.setFill( gradient);
	   			
			} else {
				
				gc.setFill(Color.GREEN.deriveColor(1, 1, 1, 0.7));
								
			}
			
			int count = 0;
			gc.beginPath();
			for( PVector point: points) {
				if( count == 0) {
					gc.moveTo(point.x, point.y);
				} else {
					gc.lineTo(point.x, point.y);
				}
				count++;
			}
			gc.closePath();
			
			// stroke
			if( Settings.get().isShapeBorderVisible()) {
				gc.stroke();
			}
			
			// fill
			gc.fill();

		} 

		// draw intersection points
		if( Settings.get().isDrawPoints()) {

			gc.setStroke(Color.RED);
			gc.setFill(Color.RED.deriveColor(1, 1, 1, 0.5));

			double w = 2;
			double h = w;
			for( PVector point: points) {
				gc.strokeOval(point.x - w / 2, point.y - h / 2, w, h);
				gc.fillOval(point.x - w / 2, point.y - h / 2, w, h);
			}
		}
		
		// user
		if( Settings.get().isUserVisible()) {
			gc.setStroke(Color.BLACK);
			gc.setFill(Color.LIGHTGRAY);
			
			double w = 5;
			double h = w;
			gc.fillOval(mouseStatus.x - w / 2, mouseStatus.y - h / 2, w, h);
			gc.strokeOval(mouseStatus.x - w / 2, mouseStatus.y - h / 2, w, h);
		}
	}
		
	
	private void drawLine( Line line) {

		GraphicsContext gc = foregroundGraphicsContext;
		
		gc.strokeLine(line.getStart().x, line.getStart().y, line.getEnd().x, line.getEnd().y);

	}
	

	/**
	 * Listeners for keyboard, mouse
	 */
	private void addInputListeners() {
		
		// capture mouse position
		scene.addEventFilter(MouseEvent.ANY, e -> {
			
			mouseStatus.setX(e.getX());
			mouseStatus.setY(e.getY());
			mouseStatus.setPrimaryButtonDown(e.isPrimaryButtonDown());
			mouseStatus.setSecondaryButtonDown(e.isSecondaryButtonDown());
			
		});
		
	}

	
	private void paintGrid( Color color) {
		
		double width = backgroundCanvas.getWidth();
		double height = backgroundCanvas.getHeight();
		
		double horizontalCellCount = Settings.get().getHorizontalCellCount();
		double cellSize = width / horizontalCellCount;
		double verticalCellCount = height / cellSize;
		
		backgroundGraphicsContext.setStroke( color);
		backgroundGraphicsContext.setLineWidth(1);

		// horizontal grid lines
		for( double row=0; row < height; row+=cellSize) {

			double y = (int) row + 0.5;
			backgroundGraphicsContext.strokeLine(0, y, width, y);

		}

		// vertical grid lines
		for( double col=0; col < width; col+=cellSize) {
			
			double x = (int) col + 0.5;
			backgroundGraphicsContext.strokeLine(x, 0, x, height);
			
		}
		
		// highlight cell in which the mouse cursor resides
		if(Settings.get().isHighlightGridCell()) {
			
			Color highlightColor = Color.LIGHTBLUE;
			
			int col = (int) ( horizontalCellCount / width * mouseStatus.getX());
			int row = (int) ( verticalCellCount / height * mouseStatus.getY());
			
			backgroundGraphicsContext.setFill(highlightColor);
			backgroundGraphicsContext.fillRect(col * cellSize, row * cellSize, cellSize, cellSize);
			
		}
		
	}
	
	/**
	 * Listeners for settings changes
	 */
	private void addSettingsListeners() {
		
		// particle size
		Settings.get().horizontalCellCountProperty().addListener((ChangeListener<Number>) (observable, oldValue, newValue) -> System.out.println( "Horizontal cell count: " + newValue));
		
		Settings.get().lineCountProperty().addListener((ChangeListener<Number>) (observable, oldValue, newValue) -> createObjects());
		Settings.get().roomIterationsProperty().addListener((ChangeListener<Number>) (observable, oldValue, newValue) -> createObjects());
	}

	/**
	 * Context menu for the canvas
	 * @param node
	 */
	public void addCanvasContextMenu( Node node) {
		
		MenuItem menuItem;
		
		// create context menu
		ContextMenu contextMenu = new ContextMenu();
		
		// add custom menu item
		menuItem = new MenuItem("Menu Item");
		menuItem.setOnAction(e -> System.out.println( "Clicked"));
		contextMenu.getItems().add( menuItem);
		
		// context menu listener
		node.setOnMousePressed(event -> {
		    if (event.isSecondaryButtonDown()) {
		        contextMenu.show(node, event.getScreenX(), event.getScreenY());
		    }
		});
	}
	
	public static void main(String[] args) {
		launch(args);
	}


}
