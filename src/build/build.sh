mkdir acpcommander 2>/dev/null

javac -d . ../*.java
if [ $? -ne 0 ]; then
   echo "compile failed, quiting"
   exit 99
fi

jar -cvfe acp_commander.jar acpcommander.acp_commander acpcommander/*.class *.class
if [ $? -ne 0 ]; then
   echo "compile failed, quiting"
   exit 99
fi

jar -cvfe acp_commander_gui.jar acp_gui acpcommander/*.class *.class
if [ $? -ne 0 ]; then
   echo "compile failed, quiting"
   exit 99
fi

rm -r acpcommander/
rm *.class
