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

import java.rmi.RemoteException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.CheckboxInput;
import de.willuhn.jameica.gui.input.DecimalInput;
import de.willuhn.jameica.gui.input.LabelInput;
import de.willuhn.jameica.gui.input.MultiInput;
import de.willuhn.jameica.gui.input.TextAreaInput;
import de.willuhn.jameica.gui.parts.Button;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Color;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.hbci.gui.input.UmsatzTypInput;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.messaging.ObjectChangedMessage;
import de.willuhn.jameica.hbci.messaging.ObjectDeletedMessage;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.rmi.UmsatzTyp;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.ApplicationException;

/**
 * Dialog for entering of a date.
 * @author René Mach
 */
public class DialogSplitTransaction extends AbstractDialog<Date> {
  private final static int NUMBER_INPUTS = 4;
  private final static int WIDTH = 800;
  private final static int HEIGHT = 650;
  
  private Umsatz mOriginal;
  private UmsatzInput[] mInputs;
  private Button mOk; 
  private CheckboxInput mAutoCalc;
  
  public DialogSplitTransaction(final Umsatz original) {
    super(POSITION_CENTER);
    
    mOriginal = original;
    UmsatzInput.INDEX_CURRENT = 0;
    
    setTitle("Umsatz aufsplitten");
    setSize(WIDTH, HEIGHT);
  }
  
  private Umsatz getUmsatzForInputNumber(final int number) {
    Umsatz result = null;
    
    if(number >= 0 && number <= 1) {
      result = mOriginal;
    }
    
    return result;
  }
  
  private double getBetragForInputNumber(final int number, double betrag) throws RemoteException {
    double result = 0;
    
    switch(number) {
      case 0: result = betrag;break;
      case 1: result = mOriginal.getBetrag() - betrag;break;
    }
    
    return result;
  }
  
  @Override
  protected void paint(Composite parent) throws Exception {
    final SimpleContainer c = new SimpleContainer(parent);
    c.addText("ACHTUNG: Der Originalumsatz wird gelöscht und durch die neuen Umsätze ersetzt. Dabei ändern sich auch die Salden der Umsätze desselben Tages, die in der Liste nach dem geteilten Umsatz stehen. Sollte der aufgeteilte Umsatz erneut abgerufen werden, kann es vorkommen, dass die aufgeteilten Umsätze von Hibiscus wieder gelöscht werden.", true, Color.ERROR);
    
    mAutoCalc = new CheckboxInput(true);
    mAutoCalc.setName("Beträge automatisch neu berechnen");
    c.addInput(mAutoCalc);
    
    final LabelInput verw = new LabelInput("Verwendungszweck");
    final LabelInput betrag = new LabelInput("Betrag");
    final LabelInput kategorie = new LabelInput("Umsatz-Kategorie");
    kategorie.setName("");
    
    final MultiInput label = new MultiInput(verw,new MultiInput(betrag,kategorie));
    label.setName("");
    
    c.addInput(label);
    
    double b = 0;
    
    b = ((int)((mOriginal.getBetrag()*100)/2))/100d;
    
    mInputs = new UmsatzInput[NUMBER_INPUTS];
    final Listener buttonUpdateListener = new Listener() {
      @Override
      public void handleEvent(Event event) {
        updateButton();
      }
    };
    final Listener calcListener = new Listener() {
      @Override
      public void handleEvent(Event event) {
        calc(event.widget);
      }
    };
    
    for(int i = 0; i < NUMBER_INPUTS; i++) {
      mInputs[i] = new UmsatzInput(getUmsatzForInputNumber(i),getBetragForInputNumber(i, b));
      mInputs[i].addTo(c, i+1, buttonUpdateListener, calcListener);
    }
    
    ButtonArea buttons = new ButtonArea();
    buttons.addButton("Abbrechen", new Action() {
      @Override
      public void handleAction(Object context) throws ApplicationException {
        
        close();
      }
    }, null, false, "process-stop.png");
    mOk = new Button("OK", new Action() {
      @Override
      public void handleAction(Object context) throws ApplicationException {
        double saldo;
        try {
          
          final DBIterator<Umsatz> it = mOriginal.getKonto().getUmsaetze(mOriginal.getDatum(), mOriginal.getDatum());
          it.setOrder("order by id ASC");
          boolean found = false;
          saldo = mOriginal.getSaldo() - mOriginal.getBetrag();
          
          while(it.hasNext()) {
            Umsatz next = it.next();
            
            if(next.getID().equals(mOriginal.getID())) {
              found = true;
            }
            else if(found) {
              next.setSaldo(next.getSaldo()-mOriginal.getBetrag());
              saldo = next.getSaldo();
              next.store();
              Application.getMessagingFactory().sendMessage(new ObjectChangedMessage(next));
            }
          }
          
          mOriginal.delete();
          
          Application.getMessagingFactory().sendMessage(new ObjectDeletedMessage(mOriginal));
          
          for(UmsatzInput input : mInputs) {
            saldo = update(input, saldo);
          }
          
          
        } catch (RemoteException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        
        close();
      }
    }, null, false, "ok.png");
    buttons.addButton(mOk);
    
    c.addButtonArea(buttons);
    
    getShell().setMinimumSize(WIDTH,HEIGHT);
    getShell().addDisposeListener(new DisposeListener() {
      @Override
      public void widgetDisposed(DisposeEvent e)
      {
        Shell shell = getShell();
        if (shell == null || shell.isDisposed())
          return;
      }
    });
    
    updateButton();
  }
  
  private void calc(final Widget source) {
    if((boolean)mAutoCalc.getValue()) {
      double betrag = 0;
      boolean found = false;
      final ArrayList<DecimalInput> validInputs = new ArrayList<>();
      
      for(UmsatzInput i : mInputs) {
        try {
          if(found) {
            if(i.mBetrag.getValue() != null) {
              validInputs.add(i.mBetrag);
            }
          }
          else if(source.equals(i.mBetrag.getControl()))  {
            found = true;
            
            if(i.mBetrag.getValue() != null) {
              betrag += (double)i.mBetrag.getValue();
            }
          }
          else if(i.mBetrag.getValue() != null) {
            betrag += (double)i.mBetrag.getValue();
          }
        }catch(Exception e) {
          e.printStackTrace();
        }
      }
      
      try {
        if(validInputs.isEmpty() && Math.abs(betrag) < Math.abs(mOriginal.getBetrag())) {
          UmsatzInput prev = null;
          
          for(UmsatzInput i : mInputs) {
            if(i.mBetrag.getValue() == null && prev != null && prev.mBetrag.getValue() != null) {
              validInputs.add(i.mBetrag);
              i.mVerwendungszweck.setValue(mInputs[0].mVerwendungszweck.getValue());
              break;
            }
            prev = i;
          }
        }
      }catch(Exception e) {}
      
      if(!validInputs.isEmpty()) {
        try {
          double part = ((int)((((mOriginal.getBetrag() - betrag)*100))/validInputs.size()))/100d;
          
          for(int i = 0; i < validInputs.size()-1; i++) {
            validInputs.get(i).setValue(part);
            betrag += part;
          }
          
          validInputs.get(validInputs.size()-1).setValue(mOriginal.getBetrag()-betrag);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
  }
  
  private double update(UmsatzInput i, Double saldo) {
    if(i.isSplit()) {
      try {
        Umsatz u = null;
        
        u = mOriginal.duplicate();
        u.setZweck(null);
        u.setZweck2(null);
        u.setWeitereVerwendungszwecke(null);
        u.setBetrag(0);
        
        if(i.mIndex == 0) {
          u.setTransactionId(mOriginal.getTransactionId());
        }
        
        String[] verw = ((String)i.mVerwendungszweck.getValue()).split("\n");
        
        if(verw.length > 2) {
          String[] weitere = new String[verw.length-2];
          System.arraycopy(verw, 2, weitere, 0, weitere.length);
          u.setWeitereVerwendungszwecke(weitere);
        }
        
        if(verw.length > 1) {
          u.setZweck2(verw[1]);
        }
        
        if(verw.length > 0) {
          u.setZweck(verw[0]);
        }
        
        u.setBetrag((double)i.mBetrag.getValue());
        u.setUmsatzTyp((UmsatzTyp)i.mUmsatzTyp.getValue());
        
        saldo += u.getBetrag();
        
        u.setSaldo(saldo);
        
        u.store();
        
        Application.getMessagingFactory().sendMessage(new ImportMessage(u));
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    return saldo;
  }
  
  private synchronized void updateButton() {
    long betrag = 0;
    
    for(int i = 0; i < mInputs.length; i++) {
      UmsatzInput input = mInputs[i];
      try {
        if(input.mBetrag.getValue() != null) {
          betrag += Math.round((double)input.mBetrag.getValue()*100);
        }
      }catch(Exception e) {
        e.printStackTrace();
      }
    }
    
    boolean enabled = false;
    
    try {
      enabled = (Math.round(mOriginal.getBetrag()*100)) == betrag;
      
      if(enabled) {
        for(int i = 0; i < mInputs.length; i++) {
          try {
          enabled = ((String)mInputs[i].mVerwendungszweck.getValue()).trim().length() > 0 && mInputs[i].mBetrag.getValue() != null;
          
          if(!enabled && i >= 2) {
            enabled = ((String)mInputs[i].mVerwendungszweck.getValue()).trim().isEmpty() && mInputs[i].mBetrag.getValue() == null;
          }
          
          if(!enabled) {
            break;
          }
          }catch(Exception e) {
            e.printStackTrace();
            enabled = false;
            break;
          }
        }
      }
    } catch (RemoteException e) {
      enabled = false;
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    mOk.setEnabled(enabled);
  }
  
  @Override
  protected void onEscape() {
    
  }
  
  @Override
  protected Date getData() throws Exception {
    return null;
  }
  
  private final static class UmsatzInput {
    private static int INDEX_CURRENT = 0;
    private int mIndex;
    private TextAreaInput mVerwendungszweck;
    private DecimalInput mBetrag;
    private UmsatzTypInput mUmsatzTyp;
    
    private UmsatzInput(final Umsatz u, double betrag) {
      mIndex = INDEX_CURRENT++;
      StringBuilder v = new StringBuilder();
      
      if(u != null) {
        try {
          if(u.getZweck() != null) {
            v.append(u.getZweck().trim());
          }
          if(u.getZweck2() != null && !u.getZweck2().trim().isEmpty()) {
            if(v.length() > 0) {
              v.append("\n");
            }
            v.append(u.getZweck2().trim());
          }
          if(u.getWeitereVerwendungszwecke() != null) {
            final String[] parts = u.getWeitereVerwendungszwecke();
            
            for(String part : parts) {
              if(part != null && !part.trim().isEmpty()) {
                if(v.length() > 0) {
                  v.append("\n");
                }
                
                v.append(part.trim());
              }
            }
          }
        } catch (RemoteException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        
      }
      
      mVerwendungszweck = new TextAreaInput(v.toString());
      mVerwendungszweck.setHeight(80);
      
      if(u == null) {
        mVerwendungszweck.setHint("Optional");
        mBetrag = new DecimalInput(new DecimalFormat());
        mBetrag.setHint("Optional");
      }
      else {
        mVerwendungszweck.setMandatory(true);
        mBetrag = new DecimalInput(betrag, new DecimalFormat("#.##"));
        mBetrag.setMandatory(true);
      }
      
      mBetrag.setMaxLength(10);
      
      try {
      //copied from de.willuhn.jameica.hbci.gui.controller UmsatzDetailControl
      int typ = UmsatzTyp.TYP_EGAL;

      UmsatzTyp ut = u != null ? u.getUmsatzTyp() : null;
      
      // wenn noch keine Kategorie zugeordnet ist, bieten wir nur die passenden an.
      if (u != null && ut == null && u.getBetrag() != 0)
        typ = (u.getBetrag() > 0 ? UmsatzTyp.TYP_EINNAHME : UmsatzTyp.TYP_AUSGABE);
      
      // Ansonsten alle - damit die zugeordnete Kategorie auch dann noch
      // noch angeboten wird, der User nachtraeglich den Kat-Typ geaendert hat.
      
      mUmsatzTyp = new UmsatzTypInput(ut, typ, true);
      mUmsatzTyp.setComment("");
      mUmsatzTyp.setName("");
      
      mUmsatzTyp.setEnabled(u == null || ((u.getFlags() & Umsatz.FLAG_NOTBOOKED) == 0));
      //end of copy
      } catch (RemoteException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    private void addTo(SimpleContainer c, int number, final Listener l, final Listener calc) {
      final MultiInput i2 = new MultiInput(mBetrag, mUmsatzTyp);
      final MultiInput i = new MultiInput(mVerwendungszweck,i2);
      i.setName(String.valueOf(number)+".");
      
      c.addInput(i);
      mVerwendungszweck.getControl().addListener(SWT.Modify, l);
      mBetrag.getControl().addListener(SWT.Modify, l);
      mBetrag.getControl().addListener(SWT.FocusOut, calc);
    }
    
    private boolean isSplit() {
      boolean result = false;
      try {
        result = mVerwendungszweck.getValue() != null && !((String)mVerwendungszweck.getValue()).trim().isEmpty() && mBetrag.getValue() != null;
      }catch(Exception e) {
        e.printStackTrace();
      }
      return result;
    }
  }
}
