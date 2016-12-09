package com.fuzz.android.salvager

import com.fuzz.android.salvage.core.Persist
import java.io.Serializable

/**
 * Description:
 *
 * @author Andrew Grosner (Fuzz)
 */
@Persist
data class Example(var name: String? = null,
                   var age: Int? = null,
                   var charSequence: Array<CharSequence>? = null,
                   var serializable: SimpleSerializable? = null)

class SimpleSerializable : Serializable {

}

@Persist
data class ParentObject(var example: Example? = null)

@Persist
data class ListExample(var list: List<ParentObject> = arrayListOf(),
                       var listString: List<String> = arrayListOf(),
                       var listSerializable: List<SimpleSerializable> = arrayListOf())