package com.fuzz.android.salvage

import com.google.common.collect.Maps
import com.raizlabs.android.dbflow.processor.utils.capitalizeFirstLetter
import com.raizlabs.android.dbflow.processor.utils.lower
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.TypeName
import java.util.*

/**
 * Description: Base interface for accessing fields
 *
 * @author Andrew Grosner (fuzz)
 */
abstract class Accessor(val propertyName: String?) {

    open val isPrimitiveTarget: Boolean = false

    abstract fun get(existingBlock: CodeBlock? = null, baseVariableName: String?): CodeBlock

    abstract fun set(existingBlock: CodeBlock? = null, baseVariableName: CodeBlock? = null): CodeBlock

    protected fun prependPropertyName(code: CodeBlock.Builder) {
        propertyName?.let {
            code.add("\$L.", propertyName)
        }
    }

    protected fun appendPropertyName(code: CodeBlock.Builder) {
        propertyName?.let {
            code.add(".\$L", propertyName)
        }
    }

    protected fun appendAccess(codeAccess: CodeBlock.Builder.() -> Unit): CodeBlock {
        val codeBuilder = CodeBlock.builder()
        prependPropertyName(codeBuilder)
        codeAccess(codeBuilder)
        return codeBuilder.build()
    }
}

fun Accessor?.isPrimitiveTarget(): Boolean = this?.isPrimitiveTarget ?: true

interface GetterSetter {

    val getterName: String
    val setterName: String
}

class VisibleScopeAccessor(propertyName: String) : Accessor(propertyName) {

    override fun set(existingBlock: CodeBlock?, baseVariableName: CodeBlock?): CodeBlock {
        val codeBlock: CodeBlock.Builder = CodeBlock.builder()
        baseVariableName?.let { codeBlock.add("\$L.", baseVariableName) }
        return codeBlock.add("\$L = \$L", propertyName, existingBlock)
                .build()
    }

    override fun get(existingBlock: CodeBlock?, baseVariableName: String?): CodeBlock {
        val codeBlock: CodeBlock.Builder = CodeBlock.builder()
        existingBlock?.let { codeBlock.add("\$L.", existingBlock) }
        return codeBlock.add(propertyName)
                .build()
    }
}

class PrivateScopeAccessor : Accessor {

    private val useIsForPrivateBooleans: Boolean
    private val isBoolean: Boolean

    private var getterName: String = ""
    private var setterName: String = ""

    constructor(propertyName: String,
                getterSetter: GetterSetter? = null,
                isBoolean: Boolean = false,
                useIsForPrivateBooleans: Boolean = false) : super(propertyName) {
        this.isBoolean = isBoolean
        this.useIsForPrivateBooleans = useIsForPrivateBooleans

        getterSetter?.let {
            getterName = getterSetter.getterName
            setterName = getterSetter.setterName
        }
    }

    override fun get(existingBlock: CodeBlock?, baseVariableName: String?): CodeBlock {
        val codeBlock: CodeBlock.Builder = CodeBlock.builder()
        existingBlock?.let { codeBlock.add("\$L.", existingBlock) }
        return codeBlock.add("\$L()", getGetterNameElement())
                .build()
    }

    override fun set(existingBlock: CodeBlock?, baseVariableName: CodeBlock?): CodeBlock {
        val codeBlock: CodeBlock.Builder = CodeBlock.builder()
        baseVariableName?.let { codeBlock.add("\$L.", baseVariableName) }
        return codeBlock.add("\$L(\$L)", getSetterNameElement(), existingBlock)
                .build()
    }

    fun getGetterNameElement(): String {
        return if (getterName.isNullOrEmpty()) {
            if (propertyName != null) {
                if (useIsForPrivateBooleans && !propertyName.startsWith("is", ignoreCase = true)) {
                    "is" + propertyName.capitalizeFirstLetter()
                } else if (!useIsForPrivateBooleans && !propertyName.startsWith("get", ignoreCase = true)) {
                    "get" + propertyName.capitalizeFirstLetter()
                } else propertyName.lower()
            } else {
                ""
            }
        } else getterName
    }

    fun getSetterNameElement(): String {
        if (propertyName != null) {
            var setElementName = propertyName
            return if (setterName.isNullOrEmpty()) {
                if (!setElementName.startsWith("set", ignoreCase = true)) {
                    if (useIsForPrivateBooleans && setElementName.startsWith("is")) {
                        setElementName = setElementName.replaceFirst("is".toRegex(), "")
                    } else if (useIsForPrivateBooleans && setElementName.startsWith("Is")) {
                        setElementName = setElementName.replaceFirst("Is".toRegex(), "")
                    }
                    "set" + setElementName.capitalizeFirstLetter()
                } else setElementName.lower()
            } else setterName
        } else return ""
    }
}

class PackagePrivateScopeAccessor(propertyName: String, packageName: String,
                                  tableClassName: String)
    : Accessor(propertyName) {

    val helperClassName: ClassName
    val internalHelperClassName: ClassName

    init {
        helperClassName = ClassName.get(packageName, "${tableClassName}_$classSuffix")
        internalHelperClassName = ClassName.get(packageName, "${tableClassName}_$classSuffix")
    }

    override fun get(existingBlock: CodeBlock?, baseVariableName: String?): CodeBlock {
        return CodeBlock.of("\$T.get\$L(\$L)", internalHelperClassName,
                propertyName.capitalizeFirstLetter(),
                existingBlock)
    }

    override fun set(existingBlock: CodeBlock?, baseVariableName: CodeBlock?): CodeBlock {
        return CodeBlock.of("\$T.set\$L(\$L, \$L)", helperClassName,
                propertyName.capitalizeFirstLetter(),
                baseVariableName,
                existingBlock)
    }

    companion object {

        val classSuffix = "PersistenceHelper"

        private val methodWrittenMap = Maps.newHashMap<ClassName, MutableList<String>>()

        fun containsField(className: ClassName, columnName: String): Boolean {
            return methodWrittenMap[className]?.contains(columnName) ?: false
        }

        /**
         * Ensures we only map and use a package private field generated access method if its necessary.
         */
        fun putElement(className: ClassName, elementName: String) {
            var list: MutableList<String>? = methodWrittenMap[className]
            if (list == null) {
                list = ArrayList<String>()
                methodWrittenMap.put(className, list)
            }
            if (!list.contains(elementName)) {
                list.add(elementName)
            }
        }
    }
}

/**
 * Wraps and casts the existing accessor
 */
class SerializableAccessor(val elementTypeName: TypeName, propertyName: String? = null)
    : Accessor(propertyName) {

    override fun get(existingBlock: CodeBlock?, baseVariableName: String?): CodeBlock {
        return appendAccess { add("\$L", existingBlock) }
    }

    override fun set(existingBlock: CodeBlock?, baseVariableName: CodeBlock?): CodeBlock {
        return appendAccess {
            add("(\$T) \$L", elementTypeName, existingBlock)
        }
    }

}

class NormalAccessor(val bundleMethodName: String, val keyFieldName: String, propertyName: String? = null)
    : Accessor(propertyName) {
    override fun get(existingBlock: CodeBlock?, baseVariableName: String?): CodeBlock {
        return appendAccess {
            addStatement("bundle.put\$L(\$L + \$L, \$L)",
                    bundleMethodName, uniqueBaseKey, keyFieldName, existingBlock)
        }
    }

    override fun set(existingBlock: CodeBlock?, baseVariableName: CodeBlock?): CodeBlock {
        return appendAccess {

        }
    }

}

class NestedAccessor(val persisterFieldName: String,
                     val keyFieldName: String,
                     val baseFieldAcessor: Accessor,
                     propertyName: String? = null) : Accessor(propertyName) {
    override fun get(existingBlock: CodeBlock?, baseVariableName: String?): CodeBlock {
        return appendAccess {
            addStatement("\$L.persist(\$L, bundle, \$L + $keyFieldName)", persisterFieldName,
                    existingBlock, uniqueBaseKey)
        }
    }

    override fun set(existingBlock: CodeBlock?, baseVariableName: CodeBlock?)
            : CodeBlock {

        return appendAccess {
            addStatement(baseFieldAcessor.set(
                    CodeBlock.of("\$L.unpack(null, bundle, \$L + $keyFieldName)",
                            persisterFieldName, uniqueBaseKey),
                    baseVariableName))
        }
    }
}

class ListAccessor(val keyFieldName: String,
                   val baseFieldAcessor: Accessor,
                   val persisterFieldName: String,
                   propertyName: String? = null) : Accessor(propertyName) {

    override fun get(existingBlock: CodeBlock?, baseVariableName: String?): CodeBlock {
        return appendAccess {
            addStatement("persistList(\$L, bundle, $uniqueBaseKey, $keyFieldName, " +
                    "$persisterFieldName)", existingBlock)
        }
    }

    override fun set(existingBlock: CodeBlock?, baseVariableName: CodeBlock?): CodeBlock {
        return appendAccess {
            addStatement(baseFieldAcessor.set(
                    CodeBlock.of("restoreList(bundle, $uniqueBaseKey, $keyFieldName, " +
                            "$persisterFieldName)"),
                    baseVariableName))
        }
    }
}

