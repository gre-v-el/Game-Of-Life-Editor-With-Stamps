package sample;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Scanner;

/*
* in cellArray:
*   000 000 000    0        0
*    r   g   b  hasColor isAlive
*
* */

public class Main extends Application {

    static int treeDepth = 7;
    static int regionSize = 8;

    static int margin = 20;

    static int universeSize = regionSize * (int)Math.pow(2, treeDepth) + 2 * margin;
    static int[] cellStatesRead;
    static int[] cellStatesWrite;
    static int[] calculated;
    static int[] calculatedToCopyFrom;
    static Quadtree qt;

    static int[] lookUpTable = new int[512];

    static double zoom = 1;
    static double tileSize = 10;
    static int windowWidth = 1200, windowHeight = 800;
    static double translationX = (windowWidth -tileSize* universeSize)/2;
    static double translationY = (windowHeight -tileSize* universeSize)/2;

    static boolean showDebugLines = false;

    static Canvas canvas = new Canvas(windowWidth, windowHeight);
    static GraphicsContext ctx = canvas.getGraphicsContext2D();

    static boolean doSimulateColors = false;
    static boolean doPlaceColors = false;
    static int currentColor = 0;


    static boolean playing = false;
    static double mouseX = 0, mouseY = 0;
    static boolean prevMouseDown = false;
    static double prevMouseDownX = 0, prevMouseDownY = 0;
    static boolean leftMouseDown = false, rightMouseDown = false;
    static double fill = 1;
    static double stepperTime = 0.1;

    enum DrawingMode{
        DRAWING,
        ERASING,
        STAMPING,
        PAINTING
    }
    static DrawingMode drawingMode = DrawingMode.STAMPING;
    static DrawingMode[] DrawingModeFromInt;


    static int[][] brush = {{1}};

    static Scene scene;
    static Stage stage;

    static BorderPane root;
    static VBox toolbar;
    static HBox brushPalette;
    static VBox colorBar;
    static ScrollPane paletteScrollPane;
    static ScrollPane toolbarScrollPane;
    static ScrollPane colorBarScrollPane;

    static Slider redSlider;
    static Slider greenSlider;
    static Slider blueSlider;
    static Rectangle colorRect;
    static boolean doPickColor = false;

    static Timeline quadtreeStepper;
    static Timeline arrayStepper;
    static Text stepTimeText;

    static boolean isUsingArray = false;
    static EventHandler<ActionEvent> evokeStepWithQuadTree;
    static EventHandler<ActionEvent> evokeStepWithArray;

    @Override
    public void start(Stage primaryStage) throws Exception{
        initLookUpTable();

        DrawingModeFromInt = DrawingMode.values();

        cellStatesRead = new int[universeSize*universeSize];
        cellStatesWrite = new int[universeSize*universeSize];
        calculated = new int[universeSize*universeSize];
        calculatedToCopyFrom = new int[universeSize*universeSize];

        qt = new Quadtree(margin, margin, universeSize-2*margin, treeDepth, true, null);

        evokeStepWithQuadTree = event -> {
            long timeStart = System.nanoTime();
            cellStatesRead = cellStatesWrite.clone();
            calculated = calculatedToCopyFrom.clone();
            qt.update();
            stepTimeText.setText("step time: " + ((double) Math.round((double) (System.nanoTime() - timeStart) / 1000) / 1000) + "ms");
        };

        evokeStepWithArray = event -> {
            long timeStart = System.nanoTime();
            cellStatesRead = cellStatesWrite.clone();
            stepForArray(margin, margin, universeSize-2*margin, universeSize-2*margin);
            stepTimeText.setText("step time: " + ((double) Math.round((double) (System.nanoTime() - timeStart) / 1000) / 1000) + "ms");
        };


        initRoot();

        initToolbar();

        initColorBar();

        initSceneAndStage();

        initControls();

        initStepperTimeLine();

        readBrushesFromFiles();

    }

    public static void initLookUpTable(){
        for (int i = 0; i < 512; i++) {
            int neighbours = 0;
            boolean cell = ((i >> 4) & 1) == 1;

            for (int j = 0; j < 9; j++) {
                neighbours += (i >> j) & 1;
            }

            neighbours -= cell?1:0;

            if(!cell) lookUpTable[i] = neighbours == 3?1:0;
            else lookUpTable[i] = (neighbours == 2 || neighbours == 3)?1:0;
        }
    }

    public static void initRoot(){
        // make borderpane
        root = new BorderPane();
        root.setStyle("-fx-background-color: #222");

        root.setCenter(canvas);

        toolbar = new VBox(30);
        toolbar.getStyleClass().add("tool-bar");
        toolbar.setAlignment(Pos.TOP_CENTER);

        toolbarScrollPane = new ScrollPane(toolbar);
        toolbarScrollPane.getStylesheets().add("sample/style.css");
        toolbarScrollPane.setFitToHeight(true);

        toolbarScrollPane.setMinWidth(190);

        toolbarScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        toolbarScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        root.setLeft(toolbarScrollPane);


        colorBar = new VBox(30);
        colorBar.getStyleClass().add("color-bar");
        colorBar.setAlignment(Pos.TOP_CENTER);
        colorBar.setPrefWidth(173);

        colorBarScrollPane = new ScrollPane(colorBar);
        colorBarScrollPane.getStylesheets().add("sample/style.css");
        colorBarScrollPane.setFitToHeight(true);

        colorBarScrollPane.setMinWidth(190);

        colorBarScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        colorBarScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        root.setRight(colorBarScrollPane);


        brushPalette = new HBox(30);
        brushPalette.getStyleClass().add("brush-palette");
        brushPalette.setAlignment(Pos.CENTER_LEFT);
        paletteScrollPane = new ScrollPane(brushPalette);
        paletteScrollPane.getStylesheets().add("sample/style.css");
        paletteScrollPane.setFitToWidth(true);

        paletteScrollPane.setMinHeight(170);

        paletteScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        paletteScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        paletteScrollPane.setOnScroll(event -> {
            if(event.getDeltaX() == 0 && event.getDeltaY() != 0) {
                paletteScrollPane.setHvalue(paletteScrollPane.getHvalue() - event.getDeltaY() / brushPalette.getWidth()*3);
            }
        });

        root.setTop(paletteScrollPane);
    }

    public static void initSceneAndStage(){
        scene = new Scene(root, windowWidth, windowHeight);
        scene.getStylesheets().add("sample/style.css");
        stage = new Stage();

        stage.setScene(scene);
        stage.show();


        canvas.widthProperty().bind(scene.widthProperty().subtract(toolbarScrollPane.widthProperty()).subtract(colorBarScrollPane.widthProperty()));
        canvas.heightProperty().bind(scene.heightProperty().subtract(paletteScrollPane.heightProperty()));

        root.widthProperty().addListener(ev -> {
            //canvas.setWidth(scene.getWidth() - toolbar.getWidth() - 5);

            windowWidth = (int)scene.getWidth();
        });
        root.heightProperty().addListener(ev -> {
            //canvas.setHeight(scene.getHeight() - scrollPane.getHeight() - 5);

            windowHeight = (int)scene.getHeight();
        });
    }

    public static void initToolbar(){
        //set up tool buttons
        Button clearButton = new Button("clear");
        clearButton.setOnMouseClicked(e -> {
            cellStatesWrite = new int[universeSize * universeSize];
            qt = new Quadtree(margin, margin, universeSize-2*margin, treeDepth, true, null);
        });
        clearButton.getStyleClass().add("text-button");
        clearButton.setPrefWidth(150);
        toolbar.getChildren().add(clearButton);

        Slider fillSlider = new Slider(0, 1, 1);
        fillSlider.setOnMouseDragged(e -> fill = fillSlider.getValue());

        Text fillText = new Text("Fill");
        fillText.setFont(new Font(18));
        fillText.setFill(new Color(1, 1, 1, 1));

        VBox fillBox = new VBox(3, fillText, fillSlider);
        fillBox.setAlignment(Pos.CENTER);

        toolbar.getChildren().add(fillBox);

        /*Button drawEraseButton = new Button("now stamping");
        drawEraseButton.setOnMouseClicked(e -> {
            switch (drawingMode){
                case DRAWING:
                    drawingMode = DrawingMode.ERASING;
                    break;
                case ERASING:
                    drawingMode = DrawingMode.STAMPING;
                    break;
                case STAMPING:
                    drawingMode = DrawingMode.PAINTING;
                    break;
                case PAINTING:
                    drawingMode = DrawingMode.DRAWING;
            }

            drawEraseButton.setText("now " + drawingMode.toString().toLowerCase());
        });
        drawEraseButton.getStyleClass().add("text-button");
        drawEraseButton.setPrefWidth(150);
        toolbar.getChildren().add(drawEraseButton);*/

        ComboBox drawingModeDropDown = new ComboBox();
        drawingModeDropDown.getItems().addAll("draw", "erase", "stamp", "paint");
        drawingModeDropDown.setOnAction(e -> {
            drawingMode = DrawingModeFromInt[drawingModeDropDown.getSelectionModel().getSelectedIndex()];
        });
        drawingModeDropDown.getSelectionModel().select(2);
        drawingModeDropDown.getStyleClass().add("text-button");
        drawingModeDropDown.setPrefWidth(150);
        toolbar.getChildren().add(drawingModeDropDown);

        /*Button fillRandomButton = new Button("fill with random");
        fillRandomButton.setOnMouseClicked(e -> fillWithRandom(cellStatesWrite, fill));
        fillRandomButton.getStyleClass().add("text-button");
        fillRandomButton.setPrefWidth(150);
        toolbar.getChildren().add(fillRandomButton);*/

        Slider speedSlider = new Slider(0, 1, 0.3859);
        speedSlider.setOnMouseDragged(e -> {
            double val = speedSlider.getValue() * speedSlider.getValue();
            double ips = 3 + val*47;
            stepperTime = 1/ips;

            quadtreeStepper.stop();
            arrayStepper.stop();
            quadtreeStepper = new Timeline(new KeyFrame(Duration.seconds(stepperTime), evokeStepWithQuadTree));
            arrayStepper = new Timeline(new KeyFrame(Duration.seconds(stepperTime), evokeStepWithArray));
            quadtreeStepper.setCycleCount(Timeline.INDEFINITE);
            arrayStepper.setCycleCount(Timeline.INDEFINITE);
            if(playing && !isUsingArray) quadtreeStepper.play();
            if(playing && isUsingArray) arrayStepper.play();
        });

        Text speedText = new Text("Speed");
        speedText.setFont(new Font(18));
        speedText.setFill(new Color(1, 1, 1, 1));

        VBox speedBox = new VBox(3, speedText, speedSlider);
        speedBox.setAlignment(Pos.CENTER);

        toolbar.getChildren().add(speedBox);

        Button playStopButton = new Button(playing?"stop":"play");
        playStopButton.setOnMouseClicked(e -> {
            playing = !playing;
            if(playing) {
                if(isUsingArray) arrayStepper.play();
                else  quadtreeStepper.play();
                playStopButton.setText("stop");
            }
            else {
                quadtreeStepper.pause();
                arrayStepper.pause();
                playStopButton.setText("play");
            }
        });
        playStopButton.getStyleClass().add("text-button");
        playStopButton.setPrefWidth(150);
        toolbar.getChildren().add(playStopButton);



        Button steppingModeButton = new Button(!isUsingArray?"switch to array":"switch to quadtree");
        steppingModeButton.setOnMouseClicked(e -> {
            isUsingArray = !isUsingArray;
            if(!isUsingArray) {
                qt.readAllFromArray();
                steppingModeButton.setText("switch to array");
                arrayStepper.pause();
                if(playing){
                    quadtreeStepper.play();
                }
            }
            else {
                qt = new Quadtree(margin, margin, universeSize-2*margin, treeDepth, true, null);
                steppingModeButton.setText("switch to quadtree");
                quadtreeStepper.pause();
                if(playing){
                    arrayStepper.play();
                }
            }
        });
        steppingModeButton.getStyleClass().add("text-button");
        steppingModeButton.setPrefWidth(150);
        toolbar.getChildren().add(steppingModeButton);

        HBox checkboxBox = new HBox(0);
        checkboxBox.setAlignment(Pos.CENTER);
        CheckBox debugCheckbox = new CheckBox();
        debugCheckbox.setFont(new Font(12));
        Text debugCheckboxText = new Text("show debug");
        debugCheckboxText.setFill(Color.WHITE);
        debugCheckboxText.setFont(new Font(18));
        checkboxBox.getChildren().addAll(debugCheckbox, debugCheckboxText);
        toolbar.getChildren().add(checkboxBox);

        debugCheckboxText.setOnMouseClicked(e -> {
            debugCheckbox.setSelected(!debugCheckbox.isSelected());
            showDebugLines = debugCheckbox.isSelected();
        });
        debugCheckbox.setOnMouseClicked(e -> {
            showDebugLines = debugCheckbox.isSelected();
        });
    }

    public static void changeColor(){
        int red = (int)redSlider.getValue();
        int green = (int)greenSlider.getValue();
        int blue = (int)blueSlider.getValue();
        currentColor = red << 8 | green << 5 | blue << 2;

        colorRect.setFill(new Color(
                (double)((currentColor>>8)&0b111)/7,
                (double)((currentColor>>5)&0b111)/7,
                (double)((currentColor>>2)&0b111)/7, 1));
    }

    public static void initColorBar(){

        HBox checkboxBox = new HBox(0);
        checkboxBox.setAlignment(Pos.CENTER);
        CheckBox colorsCheckbox = new CheckBox();
        colorsCheckbox.setFont(new Font(12));
        Text colorCheckboxText = new Text("simulate colors");
        colorCheckboxText.setFill(Color.WHITE);
        colorCheckboxText.setFont(new Font(18));
        checkboxBox.getChildren().addAll(colorsCheckbox, colorCheckboxText);
        toolbar.getChildren().add(checkboxBox);

        colorCheckboxText.setOnMouseClicked(e -> {
            colorsCheckbox.setSelected(!colorsCheckbox.isSelected());
            doSimulateColors = colorsCheckbox.isSelected();
        });
        colorsCheckbox.setOnMouseClicked(e -> {
            doSimulateColors = colorsCheckbox.isSelected();
        });
        colorBar.getChildren().add(checkboxBox);



        redSlider = new Slider(0, 7, 5);
        redSlider.setOnMouseDragged(e -> {
            redSlider.setValue(Math.round(redSlider.getValue()));
            changeColor();
        });
        redSlider.getStyleClass().add("red-slider");
        colorBar.getChildren().add(redSlider);

        greenSlider = new Slider(0, 7, 5);
        greenSlider.setOnMouseDragged(e -> {
            greenSlider.setValue(Math.round(greenSlider.getValue()));
            changeColor();
        });
        greenSlider.getStyleClass().add("green-slider");
        colorBar.getChildren().add(greenSlider);

        blueSlider = new Slider(0, 7, 5);
        blueSlider.setOnMouseDragged(e -> {
            blueSlider.setValue(Math.round(blueSlider.getValue()));
            changeColor();

        });
        blueSlider.getStyleClass().add("blue-slider");
        colorBar.getChildren().add(blueSlider);


        colorRect = new Rectangle(100, 30);
        colorRect.getStyleClass().add("color-rect");
        colorBar.getChildren().add(colorRect);

        changeColor();

        Button colorPickerButton = new Button("pick color");
        colorPickerButton.setOnMouseClicked(e -> {
            doPickColor = !doPickColor;
            scene.setCursor(Cursor.CROSSHAIR);
        });
        colorPickerButton.getStyleClass().add("text-button");
        colorPickerButton.setPrefWidth(150);
        colorBar.getChildren().add(colorPickerButton);


        HBox doColorCheckboxBox = new HBox(0);
        doColorCheckboxBox.setAlignment(Pos.CENTER);
        CheckBox doColorCheckbox = new CheckBox();
        doColorCheckbox.setFont(new Font(12));
        Text doColorCheckboxText = new Text("place colors");
        doColorCheckboxText.setFill(Color.WHITE);
        doColorCheckboxText.setFont(new Font(18));
        doColorCheckboxBox.getChildren().addAll(doColorCheckbox, doColorCheckboxText);
        toolbar.getChildren().add(doColorCheckboxBox);

        doColorCheckboxText.setOnMouseClicked(e -> {
            doColorCheckbox.setSelected(!doColorCheckbox.isSelected());
            doPlaceColors = doColorCheckbox.isSelected();
        });
        doColorCheckbox.setOnMouseClicked(e -> {
            doPlaceColors = doColorCheckbox.isSelected();
        });
        colorBar.getChildren().add(doColorCheckboxBox);

    }

    public static void initStepperTimeLine(){
        // set up stepper
        stepTimeText = new Text("step time: 0.00ms");
        stepTimeText.setFont(new Font(15));
        stepTimeText.setFill(Color.WHITE);
        toolbar.getChildren().add(stepTimeText);

        quadtreeStepper = new Timeline(new KeyFrame(Duration.seconds(stepperTime), evokeStepWithQuadTree));
        quadtreeStepper.setCycleCount(Timeline.INDEFINITE);
        if(playing && !isUsingArray) quadtreeStepper.play();

        arrayStepper = new Timeline(new KeyFrame(Duration.seconds(stepperTime), evokeStepWithArray));
        arrayStepper.setCycleCount(Timeline.INDEFINITE);
        if(playing && isUsingArray) arrayStepper.play();
    }

    public static void initControls(){

        Text drawTimeText = new Text("draw time: 0.00ms");
        drawTimeText.setFont(new Font(15));
        drawTimeText.setFill(Color.WHITE);
        toolbar.getChildren().add(drawTimeText);

        Timeline controls = new Timeline(new KeyFrame(Duration.seconds(0.03), event -> {
            long timeStart = System.nanoTime();
            draw();
            prevMouseDown = leftMouseDown;
            drawTimeText.setText("draw time: " + ((double) Math.round((double) (System.nanoTime() - timeStart) / 1000) / 1000) + "ms");
        }));
        controls.setCycleCount(Timeline.INDEFINITE);
        controls.play();

        canvas.setOnMousePressed(e -> {
            leftMouseDown = e.isPrimaryButtonDown();
            rightMouseDown = e.isSecondaryButtonDown();

            prevMouseDownX = e.getX();
            prevMouseDownY = e.getY();
        });

        canvas.setOnMouseReleased(e -> {
            if(rightMouseDown && prevMouseDownX == e.getX() && prevMouseDownY == e.getY()){

                int[][] newBrush = new int[brush[0].length][brush.length];

                for (int y = 0; y < newBrush.length; y++) {
                    for (int x = 0; x < newBrush[y].length; x++) {
                        newBrush[y][x] = brush[brush.length-1 - x][y];
                    }
                }

                brush = newBrush;
            }

            leftMouseDown = false;
            rightMouseDown = false;
        });

        scene.setOnMouseMoved(event -> {
            mouseX = event.getX() - canvas.getLayoutX();
            mouseY = event.getY() - canvas.getLayoutY();
        });

        scene.setOnMouseDragged(event -> {
            if(event.isSecondaryButtonDown()){
                double dx = event.getX() - canvas.getLayoutX() - mouseX;
                double dy = event.getY() - canvas.getLayoutY() - mouseY;

                translationX += dx;
                translationY += dy;
            }

            mouseX = event.getX() - canvas.getLayoutX();
            mouseY = event.getY() - canvas.getLayoutY();
        });

        canvas.setOnScroll(e -> {
            double mult = 1;
            if(e.getDeltaY() > 0) mult *= 1.1;
            else if(e.getDeltaY() < 0) mult /= 1.1;

            zoom *= mult;

            translationX -= mouseX/tileSize * Math.signum(e.getDeltaY());
            translationY -= mouseY/tileSize * Math.signum(e.getDeltaY());

            translationX *= mult;
            translationY *= mult;

        });
    }

    public static void readBrushesFromFiles(){
        // set up palette
        try {
            File assetsFile = new File("stamp_library/assets.txt");
            Scanner assetsScanner = new Scanner(assetsFile);
            while (assetsScanner.hasNextLine()) {
                String line = assetsScanner.nextLine();
                String[] sections = line.split(":");
                for (int i = 0; i < sections.length; i++) {
                    sections[i] = sections[i].trim();
                }

                try {
                    if (sections.length == 2 && sections[0].equals("section")) {
                        Text sectionText = new Text("section:\n" + sections[1]);
                        sectionText.setFill(Color.WHITE);
                        sectionText.setFont(new Font(25));
                        sectionText.setTextAlignment(TextAlignment.CENTER);
                        brushPalette.getChildren().add(sectionText);
                    } else if (sections.length == 3) {
                        FileInputStream fileInput = new FileInputStream("stamp_library/" + sections[0]);

                        Image image = new Image(fileInput);
                        PixelReader pixelReader = image.getPixelReader();

                        int[][] thisBrush = new int[(int) image.getHeight()][(int) image.getWidth()];

                        for (int y = 0; y < thisBrush.length; y++) {
                            for (int x = 0; x < thisBrush[y].length; x++) {

                                if(sections[1].equals("0")) { // no color
                                    thisBrush[y][x] = pixelReader.getColor(x, y).getBrightness() > 0.5 ? 1 : 0;
                                }
                                else if(sections[1].equals("1")){ // color
                                    Color pixelColor = pixelReader.getColor(x, y);
                                    int value = pixelColor.getBrightness() > 0.5?1:0;

                                    if(value == 1){
                                        value |= 0b10;

                                        int red = (int)(pixelColor.getRed()*7);
                                        int green = (int)(pixelColor.getGreen()*7);
                                        int blue = (int)(pixelColor.getBlue()*7);

                                        value = value | (red<<8);
                                        value = value | (green<<5);
                                        value = value | (blue<<2);
                                    }

                                    thisBrush[y][x] = value;
                                }
                                else {
                                    throw new Exception();
                                }

                            }
                        }

                        double aspect = image.getWidth() / image.getHeight();

                        ImageView imgView = new ImageView(new Image(new FileInputStream("stamp_library/" + sections[0]), 70 * aspect, 70, true, false));
                        Text imageText = new Text(sections[0].substring(0, sections[0].length() - 4));
                        imageText.setFill(Color.WHITE);
                        VBox buttonNode = new VBox(5, imgView, imageText);
                        buttonNode.setAlignment(Pos.CENTER);

                        Button brushButton = new Button("", buttonNode);
                        if (sections[0].equals("dot.png")) brushButton.setStyle("-fx-border-color: white;");
                        brushButton.setOnMouseClicked(e -> {
                            brush = thisBrush;
                            for (Node otherButton : brushPalette.getChildren()) {
                                if (otherButton.getClass().getSimpleName().equals("Button")) {
                                    otherButton.setStyle("-fx-border-color: black;");
                                }
                            }
                            brushButton.setStyle("-fx-border-color: white;");
                        });
                        brushButton.getStyleClass().add("image-button");
                        Tooltip buttonTooltip = new Tooltip(sections[2]);
                        buttonTooltip.setFont(new Font(20));
                        buttonTooltip.getStyleClass().add("tooltip");
                        buttonTooltip.setWrapText(true);
                        buttonTooltip.setMaxWidth(400);

                        //brushButton.setTooltip(buttonTooltip);
                        brushButton.setOnMouseEntered(e -> {
                            buttonTooltip.show(brushButton, e.getScreenX() + 15, e.getScreenY() + 15);
                        });
                        brushButton.setOnMouseMoved(e -> {
                            buttonTooltip.setAnchorX(e.getScreenX() + 15);
                            buttonTooltip.setAnchorY(e.getScreenY() + 15);
                        });
                        brushButton.setOnMouseExited(e -> {
                            buttonTooltip.hide();
                        });

                        brushPalette.getChildren().add(brushButton);

                        fileInput.close();
                    }
                    else if(sections.length != 1) {
                        throw new Exception();
                    }
                } catch (Exception e) {
                    System.out.println("couldn't read " + Arrays.toString(sections) + sections.length);
                }
            }
        }catch(Exception ignored){}
    }


    public static void stepOneCell(int x, int y){
        cellStatesWrite[y*universeSize + x] =
                lookUpTable[
                        (cellStatesRead[(y - 1) * universeSize + (x - 1)] & 1) << 8
                                | (cellStatesRead[(y - 1) * universeSize + (x    )] & 1) << 7
                                | (cellStatesRead[(y - 1) * universeSize + (x + 1)] & 1) << 6
                                | (cellStatesRead[(y    ) * universeSize + (x - 1)] & 1) << 5
                                | (cellStatesRead[(y    ) * universeSize + (x    )] & 1) << 4
                                | (cellStatesRead[(y    ) * universeSize + (x + 1)] & 1) << 3
                                | (cellStatesRead[(y + 1) * universeSize + (x - 1)] & 1) << 2
                                | (cellStatesRead[(y + 1) * universeSize + (x    )] & 1) << 1
                                | (cellStatesRead[(y + 1) * universeSize + (x + 1)] & 1)
                        ];

        if(!doSimulateColors) return;

        if( (cellStatesWrite[y*universeSize + x]&1)==1 &&
                ((cellStatesRead[(y - 1) * universeSize + (x - 1)]
                        | cellStatesRead[(y - 1) * universeSize + (x    )]
                        | cellStatesRead[(y - 1) * universeSize + (x + 1)]
                        | cellStatesRead[(y    ) * universeSize + (x - 1)]
                        | cellStatesRead[(y    ) * universeSize + (x    )]
                        | cellStatesRead[(y    ) * universeSize + (x + 1)]
                        | cellStatesRead[(y + 1) * universeSize + (x - 1)]
                        | cellStatesRead[(y + 1) * universeSize + (x    )]
                        | cellStatesRead[(y + 1) * universeSize + (x + 1)]) >> 1 & 1) == 1) {

            int red = 0, green = 0, blue = 0, count = 0;

            for (int ny = y - 1; ny <= y + 1; ny++) {
                for (int nx = x - 1; nx <= x + 1; nx++) {

                    int pixelVal = cellStatesRead[ny * universeSize + nx];

                    if ((pixelVal >> 1 & 1) > 0 && (pixelVal & 1) > 0) {
                        red += ((pixelVal >> 8) & 0b111);
                        green += ((pixelVal >> 5) & 0b111);
                        blue += ((pixelVal >> 2) & 0b111);

                        count++;
                    }
                }
            }
            if (count > 0) {
                red   = (int)Math.ceil((double)red / count);
                green = (int)Math.ceil((double)green / count);
                blue  = (int)Math.ceil((double)blue / count);

                cellStatesWrite[y * universeSize + x] |= red << 8 | green << 5 | blue << 2 | 0b10;
            }
        }
    }

    public static void stepForQuadTree(int x1, int y1, int x2, int y2){

        if(y1 < 0 || x1 < 0 || y2 >= universeSize || x2 >= universeSize) return;


        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {

                if(calculated[y*universeSize + x] == 0) {
                    stepOneCell(x, y);
                    qt.changeNumCellsFromRoot((cellStatesWrite[y * universeSize + x]&1) - (cellStatesRead[y * universeSize + x]&1), x, y);
                    calculated[y*universeSize + x] = 1;
                }

            }
        }

    }

    public static void stepForArray(int x1, int y1, int x2, int y2){

        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                stepOneCell(x, y);
            }
        }
    }

    public static int getNumNeighbours(int x, int y){
        int sum = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                sum += cellStatesRead[(y+dy)*universeSize + x+dx] & 1;
            }
        }
        return sum-cellStatesRead[y*universeSize + x] & 1;
    }

    public static void draw(){
        ctx.setFill(new Color(0, 0, 0, 1));
        ctx.fillRect(0, 0, windowWidth, windowHeight);


        int gridMouseX = (int)(mouseX/(tileSize*zoom) - translationX /(tileSize*zoom));
        int gridMouseY = (int)(mouseY/(tileSize*zoom) - translationY /(tileSize*zoom));

        int brushLeft = gridMouseX - brush[0].length/2;
        int brushRight = brushLeft + brush[0].length - 1;
        int brushTop = gridMouseY - brush.length/2;
        int brushBottom = brushTop + brush.length - 1;

        if(showDebugLines){
            ctx.setStroke(Color.GREEN);
            qt.draw(ctx, tileSize, zoom, translationX, translationY);
        }

        boolean pendingResetPicker = false;

        for (int y = 0; y < universeSize; y++) {
            for (int x = 0; x < universeSize; x++) {

                if((cellStatesWrite[y*universeSize + x]&1) == 1) {
                    int pixelVal = cellStatesWrite[y*universeSize + x];

                    if((pixelVal & 0b10) > 0){
                        ctx.setFill(new Color(
                                (double)((pixelVal>>8)&0b111)/7,
                                (double)((pixelVal>>5)&0b111)/7,
                                (double)((pixelVal>>2)&0b111)/7, 1));
                    }
                    else{
                        ctx.setFill(new Color(1, 1, 1, 1));
                    }

                    ctx.fillRect(Math.ceil(x * tileSize * zoom + translationX), Math.ceil(y * tileSize * zoom + translationY), Math.ceil(tileSize * zoom), Math.ceil(tileSize * zoom));
                }

                if(doPickColor){
                    if(leftMouseDown && x == gridMouseX && y == gridMouseY){
                        pendingResetPicker = true;
                        int newCol = cellStatesWrite[y*universeSize + x];

                        if((newCol&1) == 0){
                            redSlider.setValue(0);
                            greenSlider.setValue(0);
                            blueSlider.setValue(0);
                        }
                        else if((newCol>>1&1) == 0){
                            redSlider.setValue(7);
                            greenSlider.setValue(7);
                            blueSlider.setValue(7);
                        }
                        else {
                            redSlider.setValue((newCol >> 8) & 0b111);
                            greenSlider.setValue((newCol >> 5) & 0b111);
                            blueSlider.setValue((newCol >> 2) & 0b111);
                        }

                        changeColor();
                    }
                }
                else if (brushLeft <= x && brushRight >= x && brushTop <= y && brushBottom >= y && (brush[y - brushTop][x - brushLeft]&1) > 0) {
                    ctx.setFill(new Color(0.7, 0.3, 0.7, 0.5));
                    ctx.fillRect(Math.ceil(x * tileSize*zoom + translationX), Math.ceil(y * tileSize*zoom + translationY), Math.ceil(tileSize*zoom), Math.ceil(tileSize*zoom));

                    int placeVal = brush[y - brushTop][x - brushLeft];
                    if(doPlaceColors && (placeVal>>1&1) == 0){
                        placeVal = placeVal | currentColor | 0b10;
                    }

                    if (leftMouseDown && drawingMode == DrawingMode.DRAWING && Math.random() < fill) {
                        int n = -(cellStatesWrite[y * universeSize + x]&1) + 1;
                        cellStatesWrite[y * universeSize + x] = placeVal;
                        if (!isUsingArray) qt.changeNumCellsFromRoot(n, x, y);
                    }
                    else if (leftMouseDown && drawingMode == DrawingMode.ERASING && Math.random() < fill) {
                        int n = -(cellStatesWrite[y * universeSize + x]&1);
                        cellStatesWrite[y * universeSize + x] = 0;
                        if (!isUsingArray) qt.changeNumCellsFromRoot(n, x, y);
                    }
                    else if (!prevMouseDown && leftMouseDown && drawingMode == DrawingMode.STAMPING && Math.random() < fill) {
                        int n = -(cellStatesWrite[y * universeSize + x]&1) + 1;
                        cellStatesWrite[y * universeSize + x] = placeVal;
                        if (!isUsingArray) qt.changeNumCellsFromRoot(n, x, y);
                    }
                    else if (leftMouseDown && drawingMode == DrawingMode.PAINTING && Math.random() < fill){
                        if((cellStatesWrite[y * universeSize + x]&1) == 1){
                            cellStatesWrite[y * universeSize + x] = currentColor | 0b11;
                        }
                    }


                    ctx.setFill(new Color(1, 1, 1, 1));
                }

            }
        }
        if(pendingResetPicker) doPickColor = false;
    }

    public static void fillWithRandom(int[] intArray, double factor){
        for (int i = 0; i < intArray.length; i++) {
            int n = -(intArray[i]&1);
            intArray[i] = Math.random() > 1-factor?1:0;
            n += intArray[i]&1;

            qt.changeNumCellsFromRoot(n,  i%universeSize, i/universeSize);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
