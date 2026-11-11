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

    // 读取数值型观察属性（如 energy / maxEnergy，工件里是 int）
    // 供 M5 电量阈值判断与"充满为止"充电循环使用
    @TERM
    public int intValue(ArtifactObsProperty property) {
        return property.intValue();
    }
    
    @FORMULA
    public Formula isBound(String value) {
        return value == null ? Predicate.FALSE:Predicate.TRUE;
    }
}
