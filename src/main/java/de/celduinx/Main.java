package de.celduinx;

//TIP Zum <b>Ausführen</b> des Codes <shortcut actionId="Run"/> drücken oder
// Klicke auf das Symbol <icon src="AllIcons.Actions.Execute"/> in der Randleiste.
public class Main {
    public static void main(String[] args) {
        //TIP Drücke <shortcut actionId="ShowIntentionActions"/> mit deinem Caret an dem hervorgehobenen Text
        // IntelliJ IDEA zeigt, wie es behoben werden kann.
        System.out.printf("Hello and welcome!");

        for (int i = 1; i <= 5; i++) {
            //TIP Drücke <shortcut actionId="Debug"/>, um deinen Code zu debuggen. Wir haben <icon src="AllIcons.Debugger.Db_set_breakpoint"/> Breakpoint  gesetzt.
            // <shortcut actionId="ToggleLineBreakpoint"/> für Sie gesetzt hat; Sie können weitere hinzufügen, indem Sie <shortcut actionId="ToggleLineBreakpoint"/> drücken.
            System.out.println("i = " + i);
        }
    }
}