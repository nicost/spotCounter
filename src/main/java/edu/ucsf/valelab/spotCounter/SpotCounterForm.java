
package edu.ucsf.valelab.spotCounter;


import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

/**
 *
 * @author nico
 */
public class SpotCounterForm extends JFrame {
   private final Font ourFont_;
   
   public SpotCounterForm()
   {
      ourFont_ = new Font("Arial", Font.PLAIN, 12);
      
      this.setLayout(new MigLayout("flowx, fill, insets 8"));
      this.setTitle("SpotCounter");
      
      JPanel thePanel = new JPanel(new MigLayout(
              "flowx, fill, insets 8"));

      thePanel.add(myLabel(ourFont_, "Hello "));
      
      this.add(thePanel);
      
      
   }
   
   private JLabel myLabel(Font font, String text) {
      JLabel label = new JLabel(text);
      label.setFont(font);
      return label;
   }
   
}
