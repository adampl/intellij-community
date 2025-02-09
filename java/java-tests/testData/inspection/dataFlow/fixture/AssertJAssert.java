package org.assertj.core.api;
import org.jetbrains.annotations.Nullable;

class Sample {
  public void aMethod2(Integer someValue) {
    Object object = methodWhichCanReturnNull(someValue);

    Assertions.assertThat(object).isNotNull();
    if (<warning descr="Condition 'object == null' is always 'false'">object == null</warning>) {}
  }


  @Nullable
  private Object methodWhichCanReturnNull(Integer someValue) {
    return someValue;
  }
}
class Assertions {
  public static ObjectAssert assertThat(Object actual) {
    return new ObjectAssert(actual);
  }
}
class ObjectAssert extends AbstractAssert {
  ObjectAssert(Object obj) {}
  
  public ObjectAssert isNotNull() {
    return this;
  }
}
class AbstractAssert {}