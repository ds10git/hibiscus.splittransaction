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

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.CheckboxInput;
import de.willuhn.jameica.gui.input.DecimalInput;
import de.willuhn.jameica.gui.input.LabelInput;
import de.willuhn.jameica.gui.input.MultiInput;
import de.willuhn.jameica.gui.input.SelectInput;
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
import de.willuhn.jameica.hbci.server.UmsatzTypBean;
import de.willuhn.jameica.hbci.server.UmsatzTypUtil;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.ApplicationException;

/**
 * Dialog for entering of a date.
 * @author René Mach
 */
public class DialogSplitTransaction extends AbstractDialog<Date> {
  private static final int NUMBER_INPUTS = 4;
  private static final int WIDTH = 800;
  private static final int HEIGHT = 650;
  
  private static final de.willuhn.jameica.system.Settings SETTINGS = new de.willuhn.jameica.system.Settings(DialogSplitTransaction.class);
  
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
    
    if(mOriginal.getUmsatzTyp() != null) {
      String[] keys = SETTINGS.getList(mOriginal.getUmsatzTyp().getID(), null);
      
      if(keys != null && keys.length > 0) {
        ArrayList<Template> list = new ArrayList<>();
        list.add(new Template(null, "<Vorlage auswählen>"));
        
        for(String key : keys) {
          String[] values = SETTINGS.getList(key+"_0", null);
          
          list.add(new Template(key, mOriginal.getUmsatzTyp().getName() + " - " + values[2]));
        }
        
        final SelectInput selectInput = new SelectInput(list, null);
        selectInput.setName("Vorlagen:");
        selectInput.addListener(new Listener() {
          @Override
          public void handleEvent(Event event) {
            Template current = (Template)selectInput.getValue();
            
            if(current.mKey != null) {
              loadTemplate(current.mKey);
            }
          }
        });
        c.addInput(selectInput);
      }
    }
    
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
    final KeyListener buttonUpdateListener = new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        boolean empty = false;
        
        if(e.getSource() instanceof Text) {
          Object o = ((Text)e.getSource()).getData(MyDecimalInput.KEY_PARENT);
          
          if(o != null && o instanceof MyDecimalInput) {
            MyDecimalInput input = (MyDecimalInput)o;
            empty = input.getValue() == null;
            
            input.setWasUserEdited(!empty);
          }
        }
        
        updateButton();
      }
    };
    
    final FocusAdapter calcListener = new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        calc(e.widget);
      }
    };
    
    for(int i = 0; i < NUMBER_INPUTS; i++) {
      mInputs[i] = new UmsatzInput(getUmsatzForInputNumber(i),getBetragForInputNumber(i, b));
      mInputs[i].addTo(c, i+1, buttonUpdateListener, calcListener);
      
      if(i == 0) {
        mInputs[i].mBetrag.setWasUserEdited(true);
      }
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
      public void handleAction(Object context) throws ApplicationException {System.out.println("OK");
        double saldo;
        try {
          double original = mOriginal.getBetrag();
          String type = mOriginal.getUmsatzTyp() != null ? mOriginal.getUmsatzTyp().getID() : null;
          
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
          
          String key = null;
          
          for(int i = 0; i < mInputs.length; i++) {
            UmsatzInput input = mInputs[i];
            
            if(input.isValid()) {
              UmsatzResult result = update(input, saldo);
              saldo = result.mSaldo;
              
              if(type != null) {
                String[] parts = null;
                
                if(result.mUmsatz.getWeitereVerwendungszwecke() != null) {
                  parts = new String[4+result.mUmsatz.getWeitereVerwendungszwecke().length];
                  System.arraycopy(result.mUmsatz.getWeitereVerwendungszwecke(), 0, parts, 3, result.mUmsatz.getWeitereVerwendungszwecke().length);
                  parts[3] = result.mUmsatz.getZweck2();
                  parts[2] = result.mUmsatz.getZweck();
                }
                else if(result.mUmsatz.getZweck2() != null) {
                  parts = new String[4];
                  parts[3] = result.mUmsatz.getZweck2();
                  parts[2] = result.mUmsatz.getZweck();
                }
                else {
                  parts = new String[3];
                  parts[2] = result.mUmsatz.getZweck();
                }
                
                parts[0] = String.valueOf(result.mUmsatz.getBetrag()/original);
                parts[1] = result.mUmsatz.getUmsatzTyp() != null ? result.mUmsatz.getUmsatzTyp().getID() : "";
                
                if(i == 0) {
                  key = type+"_"+result.mUmsatz.getZweck();
                }
                
                SETTINGS.setAttribute(key+"_"+input.mIndex, parts);
              }
            }
          }
          
          if(key != null) {
            String[] keys = SETTINGS.getList(type, new String[0]);
            
            boolean found1 = false;
            
            for(String keyValue : keys) {
              if(keyValue.equals(key)) {
                found1 = true;
                break;
              }
            }
            
            if(!found1) {
              String[] keysNew = new String[keys.length+1];
              System.arraycopy(keys, 0, keysNew, 0, keys.length);
              keysNew[keysNew.length-1] = key;
              
              SETTINGS.setAttribute(type, keysNew);
            }
          }
        } catch (Exception e) {
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
      long betrag = 0;
      long original = 0;
      
      try {
        original=Math.round(mOriginal.getBetrag()*100);
      } catch (RemoteException e1) {}
      
      final ArrayList<UmsatzInput> toCalc = new ArrayList<>();
      
      for(UmsatzInput i : mInputs) {
        try {
          if((!i.hasValidValueBetrag() || !i.mBetrag.wasUserEdited())) {
            toCalc.add(i);
          }
          else if(i.hasValidValueBetrag()) {
            betrag += i.getCentValue();
          }
        }catch(Exception e) {
          e.printStackTrace();
        }
      }
      
      try {
        if(betrag != original) {
          if(!toCalc.isEmpty()) {
            for(int i = toCalc.size()-1; i > 0; i--) {
              if(((String)toCalc.get(i).mVerwendungszweck.getValue()).trim().isEmpty() && !toCalc.get(i).mBetrag.hasSourceText(source)) {
                toCalc.remove(i);
              }
            }
            
            long value = (original - betrag)/toCalc.size();
            
            for(int i = 0; i < toCalc.size(); i++) {
              if(i < toCalc.size()-1) {
                toCalc.get(i).mBetrag.setValue(value/100d);
                betrag += value;
              }
              else {
                toCalc.get(i).mBetrag.setValue((original - betrag)/100d);
              }
  
              if(toCalc.get(i).mBetrag.hasSourceText(source)) {
                toCalc.get(i).mBetrag.setWasUserEdited(true);
              }
              
              if(((String)toCalc.get(i).mVerwendungszweck.getValue()).isEmpty()) {
                toCalc.get(i).mVerwendungszweck.setValue(mInputs[0].mVerwendungszweck.getValue());
              }
            }
          }
          else {
            for(UmsatzInput input : mInputs) {
              if(input.mBetrag.hasSourceText(source)) {
                if(input.hasValidValueBetrag()) {
                  betrag -= input.getCentValue();
                }
                
                input.mBetrag.setValue((original - betrag)/100d);
              }
            }
          }
        }
      }catch(Exception e) {}
      
      updateButton();
    }
  }
  
  private UmsatzResult update(UmsatzInput i, Double saldo) {
    UmsatzResult result = new UmsatzResult(null, saldo);
    
    if(i.isValid()) {
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
        
        u.setBetrag(i.getEuroValue());
        u.setUmsatzTyp(i.getUmsatzTyp());
        
        saldo += u.getBetrag();
        
        u.setSaldo(saldo);
        
        u.store();
        
        result = new UmsatzResult(u, saldo);
        
        Application.getMessagingFactory().sendMessage(new ImportMessage(u));
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    return result;
  }
  
  private synchronized void updateButton() {
    long betrag = 0;
    
    for(int i = 0; i < mInputs.length; i++) {
      UmsatzInput input = mInputs[i];
      
      if(input == null)
        return;
      
      if(input.hasValidValueBetrag()) {
        betrag += input.getCentValue();
      }
    }
    
    boolean enabled = false;
    
    try {
      enabled = ((long)(mOriginal.getBetrag()*100)) == betrag;
      
      if(enabled) {
        for(int i = 0; i < mInputs.length; i++) {
          try {
            enabled = mInputs[i].isValid();
            
            if(!enabled && i >= 2) {
              enabled = mInputs[i].isEmpty();
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
  
  private void loadTemplate(final String key) {
    for(int i = 0; i < NUMBER_INPUTS; i++) {
      try {
        UmsatzTyp typ = mOriginal.getUmsatzTyp();
        Umsatz result = null;
        
        if(typ != null) {
          String[] values = SETTINGS.getList(key+"_"+i, null);
          
          if(values != null) {
            result = mOriginal.duplicate();
            
            if(values.length > 4) {
              String[] zwecke = new String[values.length-3];
              System.arraycopy(values, 4, zwecke, 0, zwecke.length);
              result.setWeitereVerwendungszwecke(zwecke);
            }
            else {
              result.setWeitereVerwendungszwecke(null);
            }
            
            if(values.length > 3) {
              result.setZweck2(values[3]);
            }
            else {
              result.setZweck2(null);
            }
            
            result.setZweck(values[2]);
            result.setBetrag(Math.round((Double.parseDouble(values[0]) * mOriginal.getBetrag())*100)/100d);
            
            if(!values[1].isEmpty()) {
              DBIterator<UmsatzTyp> types = UmsatzTypUtil.getAll();
              
              while(types.hasNext()) {
                UmsatzTyp value = types.next();
                
                if(values[1].equals(value.getID())) {
                  result.setUmsatzTyp(value);
                  break;
                }
              }
            }
            
            mInputs[i].setUmsatz(result);
            mInputs[i].mBetrag.setWasUserEdited(true);
          }
          else {
            mInputs[i].mBetrag.clear();
            mInputs[i].mVerwendungszweck.setValue("");
            mInputs[i].mUmsatzTyp.setPreselected(mInputs[i].mUmsatzTyp.getList().get(0));
          }
        }
      } catch (RemoteException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  
  @Override
  protected Date getData() throws Exception {
    return null;
  }
  
  private final static class UmsatzInput {
    private static int INDEX_CURRENT = 0;
    private int mIndex;
    private TextAreaInput mVerwendungszweck;
    private MyDecimalInput mBetrag;
    private UmsatzTypInput mUmsatzTyp;
    
    private UmsatzInput(final Umsatz u, double betrag) {
      mIndex = INDEX_CURRENT++;
      
      mVerwendungszweck = new TextAreaInput("");
      mVerwendungszweck.setHeight(80);
      mBetrag = new MyDecimalInput(new DecimalFormat("#.##"));
      
      try {
        //copied from de.willuhn.jameica.hbci.gui.controller UmsatzDetailControl
        int typ = UmsatzTyp.TYP_EGAL;

        UmsatzTyp ut = u != null ? u.getUmsatzTyp() : null;
        
        // wenn noch keine Kategorie zugeordnet ist, bieten wir nur die passenden an.
        if (u != null && ut == null && u.getBetrag() != 0)
          typ = (u.getBetrag() > 0 ? UmsatzTyp.TYP_EINNAHME : UmsatzTyp.TYP_AUSGABE);
        
        // Ansonsten alle - damit die zugeordnete Kategorie auch dann noch
        // angeboten wird, wenn der User nachtraeglich den Kat-Typ geaendert hat.
        
        mUmsatzTyp = new UmsatzTypInput(ut, typ, true);
        mUmsatzTyp.setComment("");
        mUmsatzTyp.setName("");
        
        mUmsatzTyp.setEnabled(u == null || ((u.getFlags() & Umsatz.FLAG_NOTBOOKED) == 0));
      //end of copy
      } catch (RemoteException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    
      setUmsatz(u);
      
      if(u == null) {
        mVerwendungszweck.setHint("Optional");
        mBetrag.setHint("Optional");
      }
      else {
        mVerwendungszweck.setMandatory(true);
        mBetrag.setValue(betrag);
        mBetrag.setMandatory(true);
      }
      
      mBetrag.setMaxLength(10);
    }
    
    private void addTo(SimpleContainer c, int number, final KeyListener l, final FocusAdapter calc) {
      final MultiInput i2 = new MultiInput(mBetrag, mUmsatzTyp);
      final MultiInput i = new MultiInput(mVerwendungszweck,i2);
      i.setName(String.valueOf(number)+".");
      
      c.addInput(i);
      mVerwendungszweck.getControl().addKeyListener(l);
      mBetrag.getControl().addKeyListener(l);
      mBetrag.getControl().addFocusListener(calc);
    }
    
    private double getEuroValue() {
      return getCentValue()/100d;
    }
    
    private void setUmsatz(Umsatz u) {
      if(u != null) {
        try {
          mBetrag.setValue(u.getBetrag());
        } catch (RemoteException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
        StringBuilder v = new StringBuilder();
        
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
          
          mVerwendungszweck.setValue(v.toString());
          
          if(u.getUmsatzTyp() != null) {
            mUmsatzTyp.setPreselected(new UmsatzTypBean(u.getUmsatzTyp()));
          }
        } catch (RemoteException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        
      }
    }
    
    private Long getCentValue() {
      Long result = null;
      
      if(mBetrag.getValue() != null) {
        result = Math.round((double)mBetrag.getValue()*100);
      }
      
      return result;
    }
    
    private boolean hasValidValueBetrag() {
      return mBetrag.isValid() && mBetrag.getValue() != null && ((double)mBetrag.getValue() != 0);
    }
    
    private boolean hasValidVerwendungszweck() {
      return mVerwendungszweck.getValue() != null && !((String)mVerwendungszweck.getValue()).trim().isEmpty();
    }
    
    private boolean isValid() {
      return hasValidValueBetrag() && hasValidVerwendungszweck();
    }
    
    private boolean isEmpty() {
      return (mVerwendungszweck.getValue() == null || ((String)mVerwendungszweck.getValue()).trim().isEmpty()) && mBetrag.getValue() == null;
    }
    
    private UmsatzTyp getUmsatzTyp() {
      return (UmsatzTyp)mUmsatzTyp.getValue();
    }
  }
  
  private static final class UmsatzResult {
    private final Umsatz mUmsatz;
    private final double mSaldo;
    
    private UmsatzResult(final Umsatz umsatz, final double saldo) {
      mUmsatz = umsatz;
      mSaldo = saldo;
    }
  }
  
  private static final class MyDecimalInput extends DecimalInput {
    private static final String KEY_PARENT = "parent";
    private boolean mWasUserEdited = false;
    
    public MyDecimalInput(DecimalFormat format) {
      super(format);
    }
    
    public MyDecimalInput(double betrag, DecimalFormat format) {
      super(betrag, format);
    }
    
    @Override
    public Control getControl() {
      Control c = null;
      
      if(text == null) {
        c = super.getControl();
        text.setData(KEY_PARENT, this);
      }
      else {
        c = super.getControl();
      }
      
      return c;
    }
    
    
    @Override
    public Object getValue() {
      // TODO Auto-generated method stub
      return isValid() ? super.getValue() : null;
    }
    
    public boolean isValid() {
      return text.getText() != null && !text.isDisposed() && !text.getText().trim().equals("-") && !text.getText().trim().equals(",") && !text.getText().trim().equals(".")
          && !text.getText().trim().equals("-,") && !text.getText().trim().equals("-.") && !text.getText().trim().isEmpty();
    }
    
    public void clear() {
      text.setText("");
      setWasUserEdited(false);
    }
    
    public boolean wasUserEdited() {
      return mWasUserEdited;
    }
    
    public boolean hasSourceText(Object source) {
      return text.equals(source);
    }

    public void setWasUserEdited(boolean wasUserEdited) {
      mWasUserEdited = wasUserEdited;
    }
  }
  
  private static final class Template {
    private final String mKey;
    private final String mName;
    
    private Template(final String key, final String name) {
      mKey = key;
      mName = name;
    }
    
    @Override
    public String toString() {
      return mName;
    }
  }
}
