package ch.ninecode.edifact

case class UNA
(
   component_data_element_separator:Int = ":".codePointAt (0),
   data_element_separator:Int = "+".codePointAt (0),
   decimal_notification:Int = ".".codePointAt (0),
   release_character:Int = "?".codePointAt (0), // ToDo: a space character means the release character is not used
   segment_terminator:Int = "'".codePointAt (0)
)
