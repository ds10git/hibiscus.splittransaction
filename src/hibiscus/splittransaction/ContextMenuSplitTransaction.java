/*
 * Hibiscus splittransaction
 * Copyright (C) 2019 René Mach (dev@tvbrowser.org)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package hibiscus.splittransaction;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.extension.Extendable;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.parts.CheckedContextMenuItem;
import de.willuhn.jameica.gui.parts.ContextMenu;
import de.willuhn.jameica.gui.parts.ContextMenuItem;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

/**
 * A class to provide the context menu entries for Hibiscus splittransaction.
 * @author René Mach
 */
public class ContextMenuSplitTransaction implements Extension {
  @Override
  public void extend(Extendable extendable) {
    if (extendable == null || !(extendable instanceof ContextMenu))
    {
      Logger.warn("invalid extendable, skipping extension");
      return;
    }
    
    ContextMenu menu = (ContextMenu) extendable;
    menu.addItem(ContextMenuItem.SEPARATOR);
    
    menu.addItem(new MyContextMenuItem("Umsatz aufsplitten...", new Action() {
      @Override
      public void handleAction(Object context) throws ApplicationException {
       /* TextMessage m = new TextMessage("DE62 5021 0212 1003 4233 53;/home/admin1/Downloads/Kontoauszug_09_2019.pdf\n"
            + "DE84 7002 2200 0020 1692 99;/home/admin1/Downloads/Kontoauszug_10_2019.pdf;m\n"
            + "FDDODEMMXXX:0020169299;/home/admin1/Downloads/Kontoauszug_11_2019.pdf\n"
            + "/home/admin1/Downloads/Kontoauszug_12_2019.pdf");
      
       MessagingFactory.getInstance().getMessagingQueue("hibiscus.ibankstatement").sendMessage(m);
       */ 
        
        DialogSplitTransaction splitDialog = new DialogSplitTransaction((Umsatz)context);
        try {
          splitDialog.open();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }));
  }
  
  /**
   * Hilfsklasse, um den Menupunkt zu deaktivieren, wenn die Buchung bereits zugeordnet ist.
   */
  private class MyContextMenuItem extends CheckedContextMenuItem
  {
    /**
     * ct.
     * @param text
     * @param a
     */
    public MyContextMenuItem(String text, Action a)
    {
      super(text, a);
      
    }

    /**
     * @see de.willuhn.jameica.gui.parts.CheckedContextMenuItem#isEnabledFor(java.lang.Object)
     */
    public boolean isEnabledFor(Object o)
    {
      boolean result = false;
      
      // Wenn wir eine ganze Liste von Buchungen haben, pruefen
      // wir nicht jede einzeln, ob sie schon in SynTAX vorhanden
      // ist. Die werden dann beim Import (weiter unten) einfach ausgesiebt.
      if (o instanceof Umsatz && !((Umsatz)o).equals(Umsatz.FLAG_CHECKED)) {
        result = true;
      }
      
      return result;
    }
    
  }
}
