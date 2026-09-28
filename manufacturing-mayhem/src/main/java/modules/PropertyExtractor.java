package modules;

import java.util.Collection;

import astra.core.Module;
import astra.formula.Formula;
import astra.formula.Predicate;
import astra.term.ListTerm;
import astra.term.Primitive;
import cartago.ArtifactObsProperty;

public class PropertyExtractor extends Module {
    @SuppressWarnings("unchecked")
    @TERM
    public ListTerm listValues(ArtifactObsProperty property) {
        ListTerm list = new ListTerm();
        for (String value : (Collection<String>) property.getValue()) {
            list.add(Primitive.newPrimitive(value));
        };

        return list;
    }

    @TERM
    public String stringValue(ArtifactObsProperty property) {
        return property.stringValue();
    }
    
    @FORMULA
    public Formula isBound(String value) {
        return value == null ? Predicate.FALSE:Predicate.TRUE;
    }
}
