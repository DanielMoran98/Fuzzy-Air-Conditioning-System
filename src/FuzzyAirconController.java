//import java.util.*;
//import javax.swing.*;
import processing.core.*;
import com.fuzzylite.*;
import com.fuzzylite.defuzzifier.*;
import com.fuzzylite.norm.s.*;
import com.fuzzylite.norm.t.*;
import com.fuzzylite.rule.*;
import com.fuzzylite.term.*;
import com.fuzzylite.variable.*;
import controlP5.*;

// Listener class to handle events on the dial control
class TargetTempListener implements ControlListener {
      private int targetTemp;
      TargetTempListener(){this.targetTemp = 18;}
      public void controlEvent(ControlEvent theEvent) {
          targetTemp = (int)theEvent.getController().getValue();
      }
      public int getTargetTemp(){return targetTemp;}
    }
// Listener class to handle events on the checkbox control
class CheckboxListener implements ControlListener {
      private int active;
      CheckboxListener(){this.active = 0;}
      public void controlEvent(ControlEvent theEvent) {
          active = (int)theEvent.getController().getValue();
      }
      public boolean isActive(){return active == 1;}
    }

public class FuzzyAirconController extends PApplet{

    private final int INITIAL_TEMP = 18;

    // Step vars to control perlin noise
    private float t1 = (float) 3;

    // Current room temperature
    private float roomTemp;
    private float targetTemp;

    // AC action (-/+)
    private float acCommand;

    // Setup some colours
    private int blue = color(0,174,253);
    private int red = color(255,0,0);
    private int black = color(0,0,0);
    private int grey = color(240,240,240);
    //private int white = color(255,255,255);
    private int green = color(0,204,102);

    // Fuzzy Logic objects
    private Engine engine;
    private InputVariable inputVariable1;
    private InputVariable inputVariable2;
    private OutputVariable outputVariable;
    private RuleBlock ruleBlock;

    private ControlP5 cp5;
    private TargetTempListener targetTempListener;
    private CheckboxListener acPowerListener;
    private Chart tempChart;

    public void settings(){
        size(800, 360);
    }
    public void createFuzzyEngine(){
        // Create the engine
        engine = new Engine();
        engine.setName("FuzzyAirconController");

        inputVariable1 = new InputVariable();
        inputVariable1.setEnabled(true);
        inputVariable1.setName("room");

        // Set the range and terms for the room temperature input
        inputVariable1.setRange(0, 40);
        inputVariable1.addTerm(new Trapezoid("verycold", 0, 0, 5, 12));
        inputVariable1.addTerm(new Triangle("cold", 6, 12, 20));
        inputVariable1.addTerm(new Triangle("warm", 14, 20, 26));
        inputVariable1.addTerm(new Triangle("hot", 20, 28, 34));
        inputVariable1.addTerm(new Trapezoid("veryhot", 28, 35, 40, 40));

        engine.addInputVariable(inputVariable1);

        inputVariable2 = new InputVariable();
        inputVariable2.setEnabled(true);
        inputVariable2.setName("target");

        // Set the range and terms for the target temperature input
        inputVariable2.setRange(0, 40);
        inputVariable2.addTerm(new Trapezoid("verycold", 0, 0, 5, 12));
        inputVariable2.addTerm(new Triangle("cold", 6, 12, 20));
        inputVariable2.addTerm(new Triangle("warm", 14, 20, 26));
        inputVariable2.addTerm(new Triangle("hot", 20, 28, 34));
        inputVariable2.addTerm(new Trapezoid("veryhot", 28, 35, 40, 40));

        engine.addInputVariable(inputVariable2);

        outputVariable = new OutputVariable();
        outputVariable.setEnabled(true);
        outputVariable.setName("command");
        outputVariable.fuzzyOutput().setAccumulation(new Maximum());
        outputVariable.setDefuzzifier(new Centroid(200));
        outputVariable.setDefaultValue(0.000);
        outputVariable.setLockValidOutput(false);
        outputVariable.setLockOutputRange(false);

        // TODO
        // Set the range and terms for the command output
        outputVariable.setRange(-10, 10);
        outputVariable.addTerm(new Trapezoid("cool",-10,-10,-6,0));
        outputVariable.addTerm(new Triangle("nochange", -1,0,1));
        outputVariable.addTerm(new Trapezoid("heat", 0,6,10,10));

        engine.addOutputVariable(outputVariable);

        ruleBlock = new RuleBlock();
        ruleBlock.setEnabled(true);
        ruleBlock.setName("Rule Block");

        ruleBlock.setConjunction(new Minimum());
        ruleBlock.setDisjunction(new Maximum());
        ruleBlock.setActivation(new Minimum());

        // TODO
        // Add rules to the rule block

        // Target: verycold/veryhot
        ruleBlock.addRule(Rule.parse("if (target is verycold) and (room is not verycold) then command is cool", engine));
        ruleBlock.addRule(Rule.parse("if (target is veryhot) and (room is not veryhot) then command is heat", engine));

        // Target: cold
        ruleBlock.addRule(Rule.parse("if (room is warm or room is hot or room is veryhot) and (target is cold) then command is cool", engine));
        ruleBlock.addRule(Rule.parse("if (room is verycold) and (target is cold) then command is heat", engine));
        ruleBlock.addRule(Rule.parse("if (room is cold) and (target is cold) then command is nochange", engine));

        // Target: warm
        ruleBlock.addRule(Rule.parse("if (target is warm) and (room is hot) then command is cool", engine));
        ruleBlock.addRule(Rule.parse("if (target is warm) and (room is cold) then command is heat", engine));
        ruleBlock.addRule(Rule.parse("if (target is warm) and (room is warm) then command is nochange", engine));

        // Target: hot
        ruleBlock.addRule(Rule.parse("if (room is cold or room is warm) and (target is hot) then command is heat", engine));
        ruleBlock.addRule(Rule.parse("if (target is hot) and (room is hot) then command is nochange", engine));
        ruleBlock.addRule(Rule.parse("if (target is hot) and (room is veryhot) then command is cool", engine));



        engine.addRuleBlock(ruleBlock);
    }
    public void setup(){
        roomTemp = INITIAL_TEMP;
        acCommand = 0f;
        // Set the fuzzy logic engine including inputs, outputs and rules
        createFuzzyEngine();

        // Create a ControlP5 object for the user interface
        cp5 = new ControlP5(this);

        // Create the target temperature dial
        cp5.addKnob("Target Temperature")
            .setRange(5,35) // Most room thermostats have this range - don't change
            .setValue(INITIAL_TEMP)
            .setPosition(180,180)
            .setRadius(65)
            .setColorCaptionLabel(black)
            .setDragDirection(Knob.VERTICAL)
            ;

        // Create a new listener for the target temperature slider
        targetTempListener = new TargetTempListener();
        // Add a listener so we can receive slider events
        cp5.getController("Target Temperature").addListener(targetTempListener);

        cp5.addCheckBox("AC Checkbox")
            .setPosition(210, 150)
            .setColorForeground(color(0))
            .setColorActive(blue)
            .setColorLabel(color(0))
            .setSize(20, 20)
            .addItem("AC On/Off", 0)
            ;
        acPowerListener = new CheckboxListener();
        // Add a listener so we can receive slider events
        cp5.getController("AC On/Off").addListener(acPowerListener);

        tempChart = cp5.addChart("Temperature")
                   .setPosition(350, 160)
                   .setColorCaptionLabel(black)
                   .setSize(400, 150)
                   .setRange(0, 40)
                   .setColorBackground(grey)
                   .setLabelVisible(true)
                   .setView(Chart.LINE) // use Chart.LINE, Chart.PIE, Chart.AREA, Chart.BAR_CENTERED
                   ;
        // Setup the dataset for outside temperature
        tempChart.addDataSet("Outside");
        tempChart.setData("Outside", new float[2000]);
        // Setup the dataset for room temperature
        tempChart.addDataSet("Room");
        tempChart.setData("Room", new float[2000]);
        tempChart.setColors("Room", red);
        // Setup the dataset for target temperature
        tempChart.addDataSet("Target");
        tempChart.setData("Target", new float[2000]);
        tempChart.setColors("Target", green);

    }

    private void drawThermometer(){
        // Draw the thermometer
        stroke(0);
        line(100,300,100,150); // Left side
        line(105,300,105,150); // Right side
        line(100,300,105,300); // Bottom
        fill(red);
        ellipse(103,310,19,19);

        // Draw the tick marks and labels
        fill(black);
        line(90, 300, 100, 300); // Zero tick
        text("0",80,305);
        line(95, (int)(300-18.75), 100, (int)(300-18.75)); // 5 tick
        line(90, (int)(300-37.5), 100, (int)(300-37.5)); // 10 tick
        text("10",73,(int)(300-37.5+5));
        line(95,(int)(300-56.25), 100, (int)(300-56.25)); // 15 tick

        line(90, 300-75, 100, 300-75); // 20 tick
        line(95, (int)(300-93.75), 100, (int)(300-93.75)); // 25 tick
        text("20",73,(int)(300-75+5));
        line(90, (int)(300-112.5), 100, (int)(300-112.5)); // 30 tick
        line(95, (int)(300-131.25), 100, (int)(300-131.25)); // 35 tick
        text("30",73,(int)(300-112.5+5));
        line(90, 300-150, 100, 300-150); // 40 tick
        text("40",73,(int)(300-150+5));

    }
    private void drawTempLevel(float lev){
        // No outline for the water
        noStroke();
        // Set the fill color
        fill(red);
        // Draw the rect for the temp level
        double ratio = 150*(lev / 40);
        rect(101,(int)(300-ratio),4,(int)(ratio+1));
    }
    private void drawInfo(float ot, float rt, float tt, float ac){
        // Output the room temp and target temp
        fill(black);
        text("Outside Temp: " + ot,50,35);
        text("Room Temp: " + rt,50,50);
        text("Target Temp: " + tt,50,65);
        text("AC Command: " + ac,50,80);

        // Chart legend
        fill(blue);
        rect(350,145, 10,10);
        fill(black);
        text("Outside", 365, 155);
        // Chart legend
        fill(red);
        rect(420,145, 10,10);
        fill(black);
        text("Room", 435, 155);
        // Chart legend
        fill(green);
        rect(480,145, 10,10);
        fill(black);
        text("Target", 495, 155);

    }

    private float fuzzyACEvaluate(float rt, float tt){
        // Load the input variables
        System.out.println("Room: "+rt+" Target: "+tt);
        inputVariable1.setInputValue(rt); // room temp
        inputVariable2.setInputValue(tt); // target temp
        // Run the engine
        engine.process();
        // Get the output
        return (float)(outputVariable.defuzzify());
    }

    // Run the system
    public void drawSystem(){
        // Clear the background
        background(255);

        // Draw all the static visual components
        drawThermometer();

        // Constant change
        float outsideTemp = noise(t1);
        // Map the demand to a value between -1 and 1.5
        outsideTemp = map(outsideTemp,0f,1f,0f,40f);
        // Calculate the difference between the outside temp and room temp
        // We will use a scaling factor to make the change in temp gradual
        float tempDelta = (roomTemp - outsideTemp) * 0.001f;

        // Get the target temperature
        targetTemp = targetTempListener.getTargetTemp();

        // Run the fuzzy engine with inputs and get controller output
        if (acPowerListener.isActive()){

            // Run the fuzzy controller on our inputs and get an output
            acCommand = fuzzyACEvaluate(roomTemp, targetTemp);
            System.out.printf("Result: AC command value is = %.4f (-10 to +10))\n",acCommand);

            // Apply the pump action to the current level
            // new roomTemp will be affected by the delta (room - outside) and the AC fuzzy output
            roomTemp = (roomTemp  - tempDelta + (acCommand * 0.01f));
        }
        else if (!acPowerListener.isActive()){
            // If the AC is not active then the room temp will
            // only be affected the outside temp
            roomTemp = roomTemp  - tempDelta;
        }

        // Update the temperature level on screen
        drawTempLevel(roomTemp);

        // Draw the instrumentation panel
        drawInfo(outsideTemp, roomTemp, targetTemp, acCommand);

        // Increment time step for Perlin noise
        t1 += 0.001;

        // Push the data from this time step on to the live graph
        tempChart.push("Outside", outsideTemp);
        tempChart.push("Room", roomTemp);
        tempChart.push("Target", targetTemp);

    }

    // Draw each frame of animation
    public void draw(){
        drawSystem();
    }

    // Main method
    public static void main(String[] args) {
        PApplet.main("FuzzyAirconController");


    }

}

