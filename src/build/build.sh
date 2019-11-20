mkdir acpcommander 2>/dev/null

javac -d . ../*.java
if [ $? -ne 0 ]; then
   echo "compile failed, quiting"
   exit
fi

jar -cvfe acp_commander.jar acpcommander.acp_commander acpcommander/*.class *.class
rm -r acpcommander/
rm *.class
