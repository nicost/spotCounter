package edu.ucsf.valelab.spotCounter;



import edu.ucsf.valelab.spotCounter.SpotCounterForm;
import ij.plugin.PlugIn;

/**
 * 
 *
 */
public class Spot_Counter implements PlugIn
{
   private SpotCounterForm form_;
   
    @Override
    public void run( String arg )
    {
       if (form_ == null) {
          form_ = new SpotCounterForm();
       }
       form_.setVisible(true);

    }
}
