/*
 * Copyright 2003-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovy.util.logging;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.codehaus.groovy.transform.LogASTTransformation;
import org.objectweb.asm.Opcodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This local transform adds a logging ability to your program using
 * Apache Commons logging. Every method call on a unbound variable named <i>log</i>
 * will be mapped to a call to the logger. For this a <i>log</i> field will be
 * inserted in the class. If the field already exists the usage of this transform
 * will cause a compilation error. The method name will be used to determine
 * what to call on the logger.
 * <pre>
 * log.name(exp)
 * </pre>is mapped to
 * <pre>
 * if (log.isNameEnabled() {
 *    log.name(exp)
 * }</pre>
 * Here name is a place holder for info, debug, warning, error, etc.
 * If the expression exp is a constant or only a variable access the method call will
 * not be transformed. But this will still cause a call on the injected logger.
 *
 * @author Hamlet D'Arcy
 * @author Matthias Cullmann
 * @since 1.8.0
 */
@java.lang.annotation.Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
@GroovyASTTransformationClass("org.codehaus.groovy.transform.LogASTTransformation")
public @interface Commons {
    String value() default "log";
    Class<? extends LogASTTransformation.LoggingStrategy> loggingStrategy() default CommonsLoggingStrategy.class;

    public  static class CommonsLoggingStrategy implements LogASTTransformation.LoggingStrategy {

        private static final ClassNode LOGGER_CLASSNODE;
        private static final ClassNode LOGGERFACTORY_CLASSNODE;

        static {
            ClassNode tmp1 = null;
            ClassNode tmp2 = null;

            try {
                tmp1 = ClassHelper.make(Class.forName("org.apache.commons.logging.Log"));
                tmp2 = ClassHelper.make(Class.forName("org.apache.commons.logging.LogFactory"));
            } catch (ClassNotFoundException e) {
                tmp1 = ClassHelper.make("org.apache.commons.logging.Log");
                tmp2 = ClassHelper.make("org.apache.commons.logging.LogFactory");
            } finally {
                LOGGER_CLASSNODE = tmp1;
                LOGGERFACTORY_CLASSNODE = tmp2;
            }
        }

        public FieldNode addLoggerFieldToClass(ClassNode classNode, String logFieldName) {
            return classNode.addField(logFieldName,
                    Opcodes.ACC_FINAL | Opcodes.ACC_TRANSIENT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE,
                    LOGGER_CLASSNODE,
                    new MethodCallExpression(
                            new ClassExpression(LOGGERFACTORY_CLASSNODE),
                            "getLog",
                            new ClassExpression(classNode)));
        }

        public boolean isLoggingMethod(String methodName) {
            return methodName.matches("fatal|error|warn|info|debug|trace");
        }

        public Expression wrapLoggingMethodCall(Expression logVariable, String methodName, Expression originalExpression) {
            MethodCallExpression condition = new MethodCallExpression(
                    logVariable,
                    "is" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1, methodName.length()) + "Enabled",
                    ArgumentListExpression.EMPTY_ARGUMENTS);

            return new TernaryExpression(
                    new BooleanExpression(condition),
                    originalExpression,
                    ConstantExpression.NULL);
        }
   }
}