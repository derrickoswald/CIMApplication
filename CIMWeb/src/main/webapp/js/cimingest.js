/**
 * @fileOverview Ingest smart meter data.
 * @name cimingest
 * @author Derrick Oswald
 * @version 1.0
 */
define
(
    ["util", "mustache", "cim", "cimmap", "cimfiles", "cimstatus", "moment", "lib/daterangepicker"],
    /**
     * @summary Functions to perform ingestion.
     * @name cimingest
     * @exports cimingest
     * @version 1.0
     */
    function (util, mustache, cim, cimmap, cimfiles, CIMStatus, moment, DateRangePicker)
    {
        const TimeZones =
            [
                { "country_code": "CI", "time_zone": "Africa/Abidjan", "utc_offset": "+00:00", "utc_dst_offset": "+00:00" },
                { "country_code": "GH", "time_zone": "Africa/Accra", "utc_offset": "+00:00", "utc_dst_offset": "+00:00" },
                { "country_code": "DZ", "time_zone": "Africa/Algiers", "utc_offset": "+01:00", "utc_dst_offset": "+01:00" },
                { "country_code": "GW", "time_zone": "Africa/Bissau", "utc_offset": "+00:00", "utc_dst_offset": "+00:00" },
                { "country_code": "EG", "time_zone": "Africa/Cairo", "utc_offset": "+02:00", "utc_dst_offset": "+02:00" },
                { "country_code": "MA", "time_zone": "Africa/Casablanca", "utc_offset": "+01:00", "utc_dst_offset": "+01:00" },
                { "country_code": "ES", "time_zone": "Africa/Ceuta", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "EH", "time_zone": "Africa/El_Aaiun", "utc_offset": "+00:00", "utc_dst_offset": "+01:00" },
                { "country_code": "ZA", "time_zone": "Africa/Johannesburg", "utc_offset": "+02:00", "utc_dst_offset": "+02:00" },
                { "country_code": "SS", "time_zone": "Africa/Juba", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "SD", "time_zone": "Africa/Khartoum", "utc_offset": "+02:00", "utc_dst_offset": "+02:00" },
                { "country_code": "NG", "time_zone": "Africa/Lagos", "utc_offset": "+01:00", "utc_dst_offset": "+01:00" },
                { "country_code": "MZ", "time_zone": "Africa/Maputo", "utc_offset": "+02:00", "utc_dst_offset": "+02:00" },
                { "country_code": "LR", "time_zone": "Africa/Monrovia", "utc_offset": "+00:00", "utc_dst_offset": "+00:00" },
                { "country_code": "KE", "time_zone": "Africa/Nairobi", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "TD", "time_zone": "Africa/Ndjamena", "utc_offset": "+01:00", "utc_dst_offset": "+01:00" },
                { "country_code": "LY", "time_zone": "Africa/Tripoli", "utc_offset": "+02:00", "utc_dst_offset": "+02:00" },
                { "country_code": "TN", "time_zone": "Africa/Tunis", "utc_offset": "+01:00", "utc_dst_offset": "+01:00" },
                { "country_code": "NA", "time_zone": "Africa/Windhoek", "utc_offset": "+02:00", "utc_dst_offset": "+02:00" },
                { "country_code": "US", "time_zone": "America/Adak", "utc_offset": "−10:00", "utc_dst_offset": "−09:00" },
                { "country_code": "US", "time_zone": "America/Anchorage", "utc_offset": "−09:00", "utc_dst_offset": "−08:00" },
                { "country_code": "BR", "time_zone": "America/Araguaina", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Buenos_Aires", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Catamarca", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Cordoba", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Jujuy", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/La_Rioja", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Mendoza", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Rio_Gallegos", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Salta", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/San_Juan", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/San_Luis", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Tucuman", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AR", "time_zone": "America/Argentina/Ushuaia", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "PY", "time_zone": "America/Asuncion", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "CA", "time_zone": "America/Atikokan", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "BR", "time_zone": "America/Bahia", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "MX", "time_zone": "America/Bahia_Banderas", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "BB", "time_zone": "America/Barbados", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "BR", "time_zone": "America/Belem", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "BZ", "time_zone": "America/Belize", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "CA", "time_zone": "America/Blanc-Sablon", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "BR", "time_zone": "America/Boa_Vista", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "CO", "time_zone": "America/Bogota", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/Boise", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "CA", "time_zone": "America/Cambridge_Bay", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "BR", "time_zone": "America/Campo_Grande", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "MX", "time_zone": "America/Cancun", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "VE", "time_zone": "America/Caracas", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "GF", "time_zone": "America/Cayenne", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "US", "time_zone": "America/Chicago", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "MX", "time_zone": "America/Chihuahua", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "CR", "time_zone": "America/Costa_Rica", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "CA", "time_zone": "America/Creston", "utc_offset": "−07:00", "utc_dst_offset": "−07:00" },
                { "country_code": "BR", "time_zone": "America/Cuiaba", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "CW", "time_zone": "America/Curacao", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "GL", "time_zone": "America/Danmarkshavn", "utc_offset": "+00:00", "utc_dst_offset": "+00:00" },
                { "country_code": "CA", "time_zone": "America/Dawson", "utc_offset": "−08:00", "utc_dst_offset": "−07:00" },
                { "country_code": "CA", "time_zone": "America/Dawson_Creek", "utc_offset": "−07:00", "utc_dst_offset": "−07:00" },
                { "country_code": "US", "time_zone": "America/Denver", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "US", "time_zone": "America/Detroit", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "CA", "time_zone": "America/Edmonton", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "BR", "time_zone": "America/Eirunepe", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "SV", "time_zone": "America/El_Salvador", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "CA", "time_zone": "America/Fort_Nelson", "utc_offset": "−07:00", "utc_dst_offset": "−07:00" },
                { "country_code": "BR", "time_zone": "America/Fortaleza", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "CA", "time_zone": "America/Glace_Bay", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "GL", "time_zone": "America/Godthab", "utc_offset": "−03:00", "utc_dst_offset": "−02:00" },
                { "country_code": "CA", "time_zone": "America/Goose_Bay", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "TC", "time_zone": "America/Grand_Turk", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "GT", "time_zone": "America/Guatemala", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "EC", "time_zone": "America/Guayaquil", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "GY", "time_zone": "America/Guyana", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "CA", "time_zone": "America/Halifax", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "CU", "time_zone": "America/Havana", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "MX", "time_zone": "America/Hermosillo", "utc_offset": "−07:00", "utc_dst_offset": "−07:00" },
                { "country_code": "US", "time_zone": "America/Indiana/Indianapolis", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "US", "time_zone": "America/Indiana/Knox", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/Indiana/Marengo", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "US", "time_zone": "America/Indiana/Petersburg", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "US", "time_zone": "America/Indiana/Tell_City", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/Indiana/Vevay", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "US", "time_zone": "America/Indiana/Vincennes", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "US", "time_zone": "America/Indiana/Winamac", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "CA", "time_zone": "America/Inuvik", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "CA", "time_zone": "America/Iqaluit", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "JM", "time_zone": "America/Jamaica", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/Juneau", "utc_offset": "−09:00", "utc_dst_offset": "−08:00" },
                { "country_code": "US", "time_zone": "America/Kentucky/Louisville", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "US", "time_zone": "America/Kentucky/Monticello", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "BO", "time_zone": "America/La_Paz", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "PE", "time_zone": "America/Lima", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/Los_Angeles", "utc_offset": "−08:00", "utc_dst_offset": "−07:00" },
                { "country_code": "BR", "time_zone": "America/Maceio", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "NI", "time_zone": "America/Managua", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "BR", "time_zone": "America/Manaus", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "MQ", "time_zone": "America/Martinique", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "MX", "time_zone": "America/Matamoros", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "MX", "time_zone": "America/Mazatlan", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "US", "time_zone": "America/Menominee", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "MX", "time_zone": "America/Merida", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/Metlakatla", "utc_offset": "−09:00", "utc_dst_offset": "−08:00" },
                { "country_code": "MX", "time_zone": "America/Mexico_City", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "PM", "time_zone": "America/Miquelon", "utc_offset": "−03:00", "utc_dst_offset": "−02:00" },
                { "country_code": "CA", "time_zone": "America/Moncton", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "MX", "time_zone": "America/Monterrey", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "UY", "time_zone": "America/Montevideo", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "BS", "time_zone": "America/Nassau", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "US", "time_zone": "America/New_York", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "CA", "time_zone": "America/Nipigon", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "US", "time_zone": "America/Nome", "utc_offset": "−09:00", "utc_dst_offset": "−08:00" },
                { "country_code": "BR", "time_zone": "America/Noronha", "utc_offset": "−02:00", "utc_dst_offset": "−02:00" },
                { "country_code": "US", "time_zone": "America/North_Dakota/Beulah", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/North_Dakota/Center", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/North_Dakota/New_Salem", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "MX", "time_zone": "America/Ojinaga", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "PA", "time_zone": "America/Panama", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "CA", "time_zone": "America/Pangnirtung", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "SR", "time_zone": "America/Paramaribo", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "US", "time_zone": "America/Phoenix", "utc_offset": "−07:00", "utc_dst_offset": "−07:00" },
                { "country_code": "TT", "time_zone": "America/Port_of_Spain", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "HT", "time_zone": "America/Port-au-Prince", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "BR", "time_zone": "America/Porto_Velho", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "PR", "time_zone": "America/Puerto_Rico", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "CL", "time_zone": "America/Punta_Arenas", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "CA", "time_zone": "America/Rainy_River", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "CA", "time_zone": "America/Rankin_Inlet", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "BR", "time_zone": "America/Recife", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "CA", "time_zone": "America/Regina", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "CA", "time_zone": "America/Resolute", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "BR", "time_zone": "America/Rio_Branco", "utc_offset": "−05:00", "utc_dst_offset": "−05:00" },
                { "country_code": "BR", "time_zone": "America/Santarem", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "CL", "time_zone": "America/Santiago", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "DO", "time_zone": "America/Santo_Domingo", "utc_offset": "−04:00", "utc_dst_offset": "−04:00" },
                { "country_code": "BR", "time_zone": "America/Sao_Paulo", "utc_offset": "−03:00", "utc_dst_offset": "−02:00" },
                { "country_code": "GL", "time_zone": "America/Scoresbysund", "utc_offset": "−01:00", "utc_dst_offset": "+00:00" },
                { "country_code": "US", "time_zone": "America/Sitka", "utc_offset": "−09:00", "utc_dst_offset": "−08:00" },
                { "country_code": "CA", "time_zone": "America/St_Johns", "utc_offset": "−03:30", "utc_dst_offset": "−02:30" },
                { "country_code": "CA", "time_zone": "America/Swift_Current", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "HN", "time_zone": "America/Tegucigalpa", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "GL", "time_zone": "America/Thule", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "CA", "time_zone": "America/Thunder_Bay", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "MX", "time_zone": "America/Tijuana", "utc_offset": "−08:00", "utc_dst_offset": "−07:00" },
                { "country_code": "CA", "time_zone": "America/Toronto", "utc_offset": "−05:00", "utc_dst_offset": "−04:00" },
                { "country_code": "CA", "time_zone": "America/Vancouver", "utc_offset": "−08:00", "utc_dst_offset": "−07:00" },
                { "country_code": "CA", "time_zone": "America/Whitehorse", "utc_offset": "−08:00", "utc_dst_offset": "−07:00" },
                { "country_code": "CA", "time_zone": "America/Winnipeg", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "US", "time_zone": "America/Yakutat", "utc_offset": "−09:00", "utc_dst_offset": "−08:00" },
                { "country_code": "CA", "time_zone": "America/Yellowknife", "utc_offset": "−07:00", "utc_dst_offset": "−06:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/Casey", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/Davis", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/DumontDUrville", "utc_offset": "+10:00", "utc_dst_offset": "+10:00" },
                { "country_code": "AU", "time_zone": "Antarctica/Macquarie", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/Mawson", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/Palmer", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/Rothera", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/Syowa", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/Troll", "utc_offset": "+00:00", "utc_dst_offset": "+02:00" },
                { "country_code": "AQ", "time_zone": "Antarctica/Vostok", "utc_offset": "+06:00", "utc_dst_offset": "+06:00" },
                { "country_code": "KZ", "time_zone": "Asia/Almaty", "utc_offset": "+06:00", "utc_dst_offset": "+06:00" },
                { "country_code": "JO", "time_zone": "Asia/Amman", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "RU", "time_zone": "Asia/Anadyr", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" },
                { "country_code": "KZ", "time_zone": "Asia/Aqtau", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "KZ", "time_zone": "Asia/Aqtobe", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "TM", "time_zone": "Asia/Ashgabat", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "KZ", "time_zone": "Asia/Atyrau", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "IQ", "time_zone": "Asia/Baghdad", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "AZ", "time_zone": "Asia/Baku", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "TH", "time_zone": "Asia/Bangkok", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "RU", "time_zone": "Asia/Barnaul", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "LB", "time_zone": "Asia/Beirut", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "KG", "time_zone": "Asia/Bishkek", "utc_offset": "+06:00", "utc_dst_offset": "+06:00" },
                { "country_code": "BN", "time_zone": "Asia/Brunei", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "RU", "time_zone": "Asia/Chita", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "MN", "time_zone": "Asia/Choibalsan", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "LK", "time_zone": "Asia/Colombo", "utc_offset": "+05:30", "utc_dst_offset": "+05:30" },
                { "country_code": "SY", "time_zone": "Asia/Damascus", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "BD", "time_zone": "Asia/Dhaka", "utc_offset": "+06:00", "utc_dst_offset": "+06:00" },
                { "country_code": "TL", "time_zone": "Asia/Dili", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "AE", "time_zone": "Asia/Dubai", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "TJ", "time_zone": "Asia/Dushanbe", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "CY", "time_zone": "Asia/Famagusta", "utc_offset": "+02:00", "utc_dst_offset": "+02:00" },
                { "country_code": "PS", "time_zone": "Asia/Gaza", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "PS", "time_zone": "Asia/Hebron", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "VN", "time_zone": "Asia/Ho_Chi_Minh", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "HK", "time_zone": "Asia/Hong_Kong", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "MN", "time_zone": "Asia/Hovd", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "RU", "time_zone": "Asia/Irkutsk", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "ID", "time_zone": "Asia/Jakarta", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "ID", "time_zone": "Asia/Jayapura", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "IL", "time_zone": "Asia/Jerusalem", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "AF", "time_zone": "Asia/Kabul", "utc_offset": "+04:30", "utc_dst_offset": "+04:30" },
                { "country_code": "RU", "time_zone": "Asia/Kamchatka", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" },
                { "country_code": "PK", "time_zone": "Asia/Karachi", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "NP", "time_zone": "Asia/Kathmandu", "utc_offset": "+05:45", "utc_dst_offset": "+05:45" },
                { "country_code": "RU", "time_zone": "Asia/Khandyga", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "IN", "time_zone": "Asia/Kolkata", "utc_offset": "+05:30", "utc_dst_offset": "+05:30" },
                { "country_code": "RU", "time_zone": "Asia/Krasnoyarsk", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "MY", "time_zone": "Asia/Kuala_Lumpur", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "MY", "time_zone": "Asia/Kuching", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "MO", "time_zone": "Asia/Macau", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "RU", "time_zone": "Asia/Magadan", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "ID", "time_zone": "Asia/Makassar", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "PH", "time_zone": "Asia/Manila", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "RU", "time_zone": "Asia/Novokuznetsk", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "RU", "time_zone": "Asia/Novosibirsk", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "RU", "time_zone": "Asia/Omsk", "utc_offset": "+06:00", "utc_dst_offset": "+06:00" },
                { "country_code": "KZ", "time_zone": "Asia/Oral", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "ID", "time_zone": "Asia/Pontianak", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "KP", "time_zone": "Asia/Pyongyang", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "QA", "time_zone": "Asia/Qatar", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "KZ", "time_zone": "Asia/Qyzylorda", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "SA", "time_zone": "Asia/Riyadh", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "RU", "time_zone": "Asia/Sakhalin", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "UZ", "time_zone": "Asia/Samarkand", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "KR", "time_zone": "Asia/Seoul", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "CN", "time_zone": "Asia/Shanghai", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "SG", "time_zone": "Asia/Singapore", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "RU", "time_zone": "Asia/Srednekolymsk", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "TW", "time_zone": "Asia/Taipei", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "UZ", "time_zone": "Asia/Tashkent", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "GE", "time_zone": "Asia/Tbilisi", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "IR", "time_zone": "Asia/Tehran", "utc_offset": "+03:30", "utc_dst_offset": "+04:30" },
                { "country_code": "BT", "time_zone": "Asia/Thimphu", "utc_offset": "+06:00", "utc_dst_offset": "+06:00" },
                { "country_code": "JP", "time_zone": "Asia/Tokyo", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "RU", "time_zone": "Asia/Tomsk", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "MN", "time_zone": "Asia/Ulaanbaatar", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "CN", "time_zone": "Asia/Urumqi", "utc_offset": "+06:00", "utc_dst_offset": "+06:00" },
                { "country_code": "RU", "time_zone": "Asia/Ust-Nera", "utc_offset": "+10:00", "utc_dst_offset": "+10:00" },
                { "country_code": "RU", "time_zone": "Asia/Vladivostok", "utc_offset": "+10:00", "utc_dst_offset": "+10:00" },
                { "country_code": "RU", "time_zone": "Asia/Yakutsk", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "MM", "time_zone": "Asia/Yangon", "utc_offset": "+06:30", "utc_dst_offset": "+06:30" },
                { "country_code": "RU", "time_zone": "Asia/Yekaterinburg", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "AM", "time_zone": "Asia/Yerevan", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "PT", "time_zone": "Atlantic/Azores", "utc_offset": "−01:00", "utc_dst_offset": "+00:00" },
                { "country_code": "BM", "time_zone": "Atlantic/Bermuda", "utc_offset": "−04:00", "utc_dst_offset": "−03:00" },
                { "country_code": "ES", "time_zone": "Atlantic/Canary", "utc_offset": "+00:00", "utc_dst_offset": "+01:00" },
                { "country_code": "CV", "time_zone": "Atlantic/Cape_Verde", "utc_offset": "−01:00", "utc_dst_offset": "−01:00" },
                { "country_code": "FO", "time_zone": "Atlantic/Faroe", "utc_offset": "+00:00", "utc_dst_offset": "+01:00" },
                { "country_code": "PT", "time_zone": "Atlantic/Madeira", "utc_offset": "+00:00", "utc_dst_offset": "+01:00" },
                { "country_code": "IS", "time_zone": "Atlantic/Reykjavik", "utc_offset": "+00:00", "utc_dst_offset": "+00:00" },
                { "country_code": "GS", "time_zone": "Atlantic/South_Georgia", "utc_offset": "−02:00", "utc_dst_offset": "−02:00" },
                { "country_code": "FK", "time_zone": "Atlantic/Stanley", "utc_offset": "−03:00", "utc_dst_offset": "−03:00" },
                { "country_code": "AU", "time_zone": "Australia/Adelaide", "utc_offset": "+09:30", "utc_dst_offset": "+10:30" },
                { "country_code": "AU", "time_zone": "Australia/Brisbane", "utc_offset": "+10:00", "utc_dst_offset": "+10:00" },
                { "country_code": "AU", "time_zone": "Australia/Broken_Hill", "utc_offset": "+09:30", "utc_dst_offset": "+10:30" },
                { "country_code": "AU", "time_zone": "Australia/Currie", "utc_offset": "+10:00", "utc_dst_offset": "+11:00" },
                { "country_code": "AU", "time_zone": "Australia/Darwin", "utc_offset": "+09:30", "utc_dst_offset": "+09:30" },
                { "country_code": "AU", "time_zone": "Australia/Eucla", "utc_offset": "+08:45", "utc_dst_offset": "+08:45" },
                { "country_code": "AU", "time_zone": "Australia/Hobart", "utc_offset": "+10:00", "utc_dst_offset": "+11:00" },
                { "country_code": "AU", "time_zone": "Australia/Lindeman", "utc_offset": "+10:00", "utc_dst_offset": "+10:00" },
                { "country_code": "AU", "time_zone": "Australia/Lord_Howe", "utc_offset": "+10:30", "utc_dst_offset": "+11:00" },
                { "country_code": "AU", "time_zone": "Australia/Melbourne", "utc_offset": "+10:00", "utc_dst_offset": "+11:00" },
                { "country_code": "AU", "time_zone": "Australia/Perth", "utc_offset": "+08:00", "utc_dst_offset": "+08:00" },
                { "country_code": "AU", "time_zone": "Australia/Sydney", "utc_offset": "+10:00", "utc_dst_offset": "+11:00" },
                { "country_code": "", "time_zone": "Etc/UTC", "utc_offset": "+00:00", "utc_dst_offset": "+00:00" },
                { "country_code": "NL", "time_zone": "Europe/Amsterdam", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "AD", "time_zone": "Europe/Andorra", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "RU", "time_zone": "Europe/Astrakhan", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "GR", "time_zone": "Europe/Athens", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "RS", "time_zone": "Europe/Belgrade", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "DE", "time_zone": "Europe/Berlin", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "BE", "time_zone": "Europe/Brussels", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "RO", "time_zone": "Europe/Bucharest", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "HU", "time_zone": "Europe/Budapest", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "MD", "time_zone": "Europe/Chisinau", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "DK", "time_zone": "Europe/Copenhagen", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "IE", "time_zone": "Europe/Dublin", "utc_offset": "+00:00", "utc_dst_offset": "+01:00" },
                { "country_code": "GI", "time_zone": "Europe/Gibraltar", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "FI", "time_zone": "Europe/Helsinki", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "TR", "time_zone": "Europe/Istanbul", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "RU", "time_zone": "Europe/Kaliningrad", "utc_offset": "+02:00", "utc_dst_offset": "+02:00" },
                { "country_code": "UA", "time_zone": "Europe/Kiev", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "RU", "time_zone": "Europe/Kirov", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "PT", "time_zone": "Europe/Lisbon", "utc_offset": "+00:00", "utc_dst_offset": "+01:00" },
                { "country_code": "GB", "time_zone": "Europe/London", "utc_offset": "+00:00", "utc_dst_offset": "+01:00" },
                { "country_code": "LU", "time_zone": "Europe/Luxembourg", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "ES", "time_zone": "Europe/Madrid", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "MT", "time_zone": "Europe/Malta", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "BY", "time_zone": "Europe/Minsk", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "MC", "time_zone": "Europe/Monaco", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "RU", "time_zone": "Europe/Moscow", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "CY", "time_zone": "Asia/Nicosia", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "NO", "time_zone": "Europe/Oslo", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "FR", "time_zone": "Europe/Paris", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "CZ", "time_zone": "Europe/Prague", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "LV", "time_zone": "Europe/Riga", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "IT", "time_zone": "Europe/Rome", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "RU", "time_zone": "Europe/Samara", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "RU", "time_zone": "Europe/Saratov", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "UA", "time_zone": "Europe/Simferopol", "utc_offset": "+03:00", "utc_dst_offset": "+03:00" },
                { "country_code": "BG", "time_zone": "Europe/Sofia", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "SE", "time_zone": "Europe/Stockholm", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "EE", "time_zone": "Europe/Tallinn", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "AL", "time_zone": "Europe/Tirane", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "RU", "time_zone": "Europe/Ulyanovsk", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "UA", "time_zone": "Europe/Uzhgorod", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "AT", "time_zone": "Europe/Vienna", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "LT", "time_zone": "Europe/Vilnius", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "RU", "time_zone": "Europe/Volgograd", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "PL", "time_zone": "Europe/Warsaw", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "UA", "time_zone": "Europe/Zaporozhye", "utc_offset": "+02:00", "utc_dst_offset": "+03:00" },
                { "country_code": "CH", "time_zone": "Europe/Zurich", "utc_offset": "+01:00", "utc_dst_offset": "+02:00" },
                { "country_code": "IO", "time_zone": "Indian/Chagos", "utc_offset": "+06:00", "utc_dst_offset": "+06:00" },
                { "country_code": "CX", "time_zone": "Indian/Christmas", "utc_offset": "+07:00", "utc_dst_offset": "+07:00" },
                { "country_code": "CC", "time_zone": "Indian/Cocos", "utc_offset": "+06:30", "utc_dst_offset": "+06:30" },
                { "country_code": "TF", "time_zone": "Indian/Kerguelen", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "SC", "time_zone": "Indian/Mahe", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "MV", "time_zone": "Indian/Maldives", "utc_offset": "+05:00", "utc_dst_offset": "+05:00" },
                { "country_code": "MU", "time_zone": "Indian/Mauritius", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "RE", "time_zone": "Indian/Reunion", "utc_offset": "+04:00", "utc_dst_offset": "+04:00" },
                { "country_code": "WS", "time_zone": "Pacific/Apia", "utc_offset": "+13:00", "utc_dst_offset": "+14:00" },
                { "country_code": "NZ", "time_zone": "Pacific/Auckland", "utc_offset": "+12:00", "utc_dst_offset": "+13:00" },
                { "country_code": "PG", "time_zone": "Pacific/Bougainville", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "NZ", "time_zone": "Pacific/Chatham", "utc_offset": "+12:45", "utc_dst_offset": "+13:45" },
                { "country_code": "FM", "time_zone": "Pacific/Chuuk", "utc_offset": "+10:00", "utc_dst_offset": "+10:00" },
                { "country_code": "CL", "time_zone": "Pacific/Easter", "utc_offset": "−06:00", "utc_dst_offset": "−05:00" },
                { "country_code": "VU", "time_zone": "Pacific/Efate", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "KI", "time_zone": "Pacific/Enderbury", "utc_offset": "+13:00", "utc_dst_offset": "+13:00" },
                { "country_code": "TK", "time_zone": "Pacific/Fakaofo", "utc_offset": "+13:00", "utc_dst_offset": "+13:00" },
                { "country_code": "FJ", "time_zone": "Pacific/Fiji", "utc_offset": "+12:00", "utc_dst_offset": "+13:00" },
                { "country_code": "TV", "time_zone": "Pacific/Funafuti", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" },
                { "country_code": "EC", "time_zone": "Pacific/Galapagos", "utc_offset": "−06:00", "utc_dst_offset": "−06:00" },
                { "country_code": "PF", "time_zone": "Pacific/Gambier", "utc_offset": "−09:00", "utc_dst_offset": "−09:00" },
                { "country_code": "SB", "time_zone": "Pacific/Guadalcanal", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "GU", "time_zone": "Pacific/Guam", "utc_offset": "+10:00", "utc_dst_offset": "+10:00" },
                { "country_code": "US", "time_zone": "Pacific/Honolulu", "utc_offset": "−10:00", "utc_dst_offset": "−10:00" },
                { "country_code": "KI", "time_zone": "Pacific/Kiritimati", "utc_offset": "+14:00", "utc_dst_offset": "+14:00" },
                { "country_code": "FM", "time_zone": "Pacific/Kosrae", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "MH", "time_zone": "Pacific/Kwajalein", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" },
                { "country_code": "MH", "time_zone": "Pacific/Majuro", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" },
                { "country_code": "PF", "time_zone": "Pacific/Marquesas", "utc_offset": "−09:30", "utc_dst_offset": "−09:30" },
                { "country_code": "NR", "time_zone": "Pacific/Nauru", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" },
                { "country_code": "NU", "time_zone": "Pacific/Niue", "utc_offset": "−11:00", "utc_dst_offset": "−11:00" },
                { "country_code": "NF", "time_zone": "Pacific/Norfolk", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "NC", "time_zone": "Pacific/Noumea", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "AS", "time_zone": "Pacific/Pago_Pago", "utc_offset": "−11:00", "utc_dst_offset": "−11:00" },
                { "country_code": "PW", "time_zone": "Pacific/Palau", "utc_offset": "+09:00", "utc_dst_offset": "+09:00" },
                { "country_code": "PN", "time_zone": "Pacific/Pitcairn", "utc_offset": "−08:00", "utc_dst_offset": "−08:00" },
                { "country_code": "FM", "time_zone": "Pacific/Pohnpei", "utc_offset": "+11:00", "utc_dst_offset": "+11:00" },
                { "country_code": "PG", "time_zone": "Pacific/Port_Moresby", "utc_offset": "+10:00", "utc_dst_offset": "+10:00" },
                { "country_code": "CK", "time_zone": "Pacific/Rarotonga", "utc_offset": "−10:00", "utc_dst_offset": "−10:00" },
                { "country_code": "PF", "time_zone": "Pacific/Tahiti", "utc_offset": "−10:00", "utc_dst_offset": "−10:00" },
                { "country_code": "KI", "time_zone": "Pacific/Tarawa", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" },
                { "country_code": "TO", "time_zone": "Pacific/Tongatapu", "utc_offset": "+13:00", "utc_dst_offset": "+14:00" },
                { "country_code": "UM", "time_zone": "Pacific/Wake", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" },
                { "country_code": "WF", "time_zone": "Pacific/Wallis", "utc_offset": "+12:00", "utc_dst_offset": "+12:00" }
            ];

        function tz_option (selected, zone)
        {
            const option =
            `
            <option${selected === zone.time_zone ? " selected" : ""} value="${zone.time_zone}">${zone.time_zone} (${zone.utc_offset}/${zone.utc_dst_offset})</option>`;
            return (option);
        }

        // function derive_work_dir (file)
        // {
        //     let ret = "/simulation/";
        //     try
        //     {
        //         let url = new URL (file);
        //         const protocol = url.protocol;
        //         switch (protocol)
        //         {
        //             case "hdfs:":
        //                 url = new URL (file.replace ("hdfs:", "http:"));
        //                 const last1 = url.pathname.lastIndexOf ("/", file.length - 1);
        //                 ret = protocol + "//" + url.host + ((last1 !== -1) ? url.pathname.substring (0, last1) : "") + ret;
        //                 break;
        //             case "file:":
        //                 const last2 = url.pathname.lastIndexOf ("/", file.length - 1);
        //                 ret = protocol + "//" + ((last2 !== -1) ? url.pathname.substring (0, last2) : "") + ret;
        //                 break;
        //         }
        //     }
        //     catch (error)
        //     {
        //     }
        //     return (ret)
        // }

        /**
         * @summary Execute ingest.
         * @description Perform an ingest operation.
         * @param {string} id a unique id for this run
         * @return a Promise to resolve or reject the ingest
         * @function ingest
         * @memberOf module:cimingest
         */
        function ingest (id)
        {
            // ToDo: validation
            const job = {
                id: id,
                mapping: document.getElementById ("mapping_file").value,
                metercol: document.getElementById ("metercol").value,
                mridcol: document.getElementById ("mridcol").value,
                timezone: document.getElementById ("timezone").value,
                timespan: {
                    mintime: Number (document.getElementById ("mintime").value),
                    maxtime: Number (document.getElementById ("maxtime").value)
                },
                format: document.getElementById ("file_format").value,
                nocopy: true, // ToDo can we make this an option with uploads?
                datafiles: [document.getElementById ("data_file").value],
                keyspace: document.getElementById ("cassandra_keyspace").value,
                replication: Number (document.getElementById ("cassandra_replication").value)

                // workdir: derive_work_dir (cimmap.get_loaded ().files[0])
            };

            const url = util.home () + "cim/ingest";
            return (
                util.makeRequest ("POST", url, JSON.stringify (job, null, 4)).then (
                    (xmlhttp) =>
                    {
                        return (
                            new Promise (
                                function (resolve, reject)
                                {
                                    try
                                    {
                                        const resp = JSON.parse (xmlhttp.responseText);
                                        if (resp.status === "OK")
                                            resolve (resp.result);
                                        else
                                            reject (resp.message);
                                    }
                                    catch (exception)
                                    {
                                        reject (exception.toString ());
                                    }
                                }
                            )
                        );
                    }
                )
            );
        }

        function getRandomInt (max)
        {
            return (Math.floor (Math.random () * Math.floor (max)));
        }

        /**
         * @summary Execute ingest process.
         * @description Perform an ingest to read smart meter data files into Cassandra.
         * @param {object} event - optional, the click event
         * @function do_ingest
         * @memberOf module:cimingest
         */
        function do_ingest (event)
        {
            const id = "Ingest" + getRandomInt (1e9);
            const status = new CIMStatus (id);
            function successCallback (data)
            {
                status.stop();
                document.getElementById ("ingest_results").innerHTML = "<pre>" + JSON.stringify (data, null, 4) + "</pre>";
            }

            function failureCallback (message)
            {
                status.stop();
                alert ("ingest failed: " + JSON.stringify (message, null, 4));
            }
            status.start();
            ingest (id).then (successCallback, failureCallback);
        }

        function setDateRange (start, end)
        {
            document.getElementById ("mintime").value = start.valueOf ().toString ();
            document.getElementById ("maxtime").value = end.valueOf ().toString ();
            console.log (
                JSON.stringify (
                {
                    start: start.toISOString ().replace ("Z", "+0000"), // "2018-04-24T19:24:27.884Z"
                    end: end.toISOString ().replace ("Z", "+0000")
                },
                null, 4));
        }

        function getFiles ()
        {
            return (
                    cimfiles.fetch ("\\").then (
                            (response) =>
                            {
                                if (response.status === "OK")
                                {
                                    let csv = response.result.files.filter (file => file.path.toLowerCase ().endsWith (".csv"));
                                    const file_template =
                                            `
                                {{#files}}
                                    <option value="{{root}}{{path}}">{{path}}</option>
                                {{/files}}
                                `;
                                    document.getElementById ("mapping_file").innerHTML = mustache.render (file_template, { root: response.result.root, files: csv });
                                    document.getElementById ("data_file").innerHTML = mustache.render (file_template, response.result);
                                }
                                else
                                    alert (response.message);
                            }
                    )
            );
        }

        /**
         * @summary Render the ingest page.
         * @description Uses mustache to create HTML DOM elements that display the ingest options.
         * @function initialize
         * @memberOf module:cimingest
         */
        function initialize ()
        {
            document.getElementById ("ingest").innerHTML = "";
            const ingest_template =
                `
<div class='container'>
  <div class='row justify-content-center'>
    <div class='col-12' style='margin-top: 40px;'>
      <form id='analysis_form' role='form' style='width: 100%'>
        <h3>Ingest</h3>
        <h4>Cassandra Configuration</h4>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='cassandra_keyspace'>Cassandra keyspace</label>
          <div class='col-sm-4'>
            <input id='cassandra_keyspace' class='form-control' type='text' name='cassandra_keyspace' aria-describedby='cassandra_keyspaceHelp' value='cimapplication'>
            <small id='cassandra_keyspaceHelp' class='form-text text-muted'>The Cassandra target keyspace for smart meter data.<br>Table <b>measured_data</b>.</small>
          </div>
          <label class='col-sm-2 col-form-label' for='cassandra_replication'>Cassandra replication</label>
          <div class='col-sm-4'>
            <input id='cassandra_replication' class='form-control' type='text' name='cassandra_replication' aria-describedby='cassandra_replicationHelp' value='1'>
            <small id='cassandra_replicationHelp' class='form-text text-muted'>Cassandra keyspace replication.<br>Used only if creating the keyspace.</small>
          </div>
        </div>
        <h4>Meter ⇒ mRID Resolution</h4>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='mapping_file'>Mapping file</label>
          <div class='col-sm-10'>
            <select id="mapping_file" class="form-control custom-select" name='mapping_file' aria-describedby="mapping_fileHelp">
            </select>
            <small id='mapping_fileHelp' class='form-text text-muted'>Comma Separated Value (CSV) file with correspondence between meter ID (CH#) and CIM mRID.</small>
          </div>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='metercol'>Smart meter ID column</label>
          <div class='col-sm-4'>
            <input id='metercol' class='form-control' type='text' name='metercol' aria-describedby='metercolHelp' value='Messpunktbezeichnung'>
            <small id='metercolHelp' class='form-text text-muted'>Column name in mapping file with meter ID (CH#) values.</small>
          </div>
          <label class='col-sm-2 col-form-label' for='mridcol'>CIM mRID column</label>
          <div class='col-sm-4'>
            <input id='mridcol' class='form-control' type='text' name='mridcol' aria-describedby='mridcolHelp' value='NISNr'>
            <small id='mridcolHelp' class='form-text text-muted'>Column name in mapping file with mRID values.</small>
          </div>
        </div>
        <h4>Meter Data Configuration</h4>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='file_format'>Meter data format</label>
          <div class='col-sm-4'>
            <select id="file_format" class="form-control custom-select" name='file_format' aria-describedby="file_formatHelp">
              <option value="LPEx" selected>LPEx</option>
              <option value="Belvis">Belvis</option>
              <option value="MSCONS">MSCONS</option>
            </select>
            <small id='file_formatHelp' class='form-text text-muted'>File format of input files, either Belvis, or LPEx.</small>
          </div>
          <label class='col-sm-2 col-form-label' for='timezone'>Time zone</label>
          <div class='col-sm-4'>
            <select id='timezone' class='form-control custom-select' name='timezone' aria-describedby='timezoneHelp'>
            ${TimeZones.map (tz_option.bind (this, "Europe/Berlin"))}
            </select>
            <small id='timezoneHelp' class='form-text text-muted'>Time zone for smart meter readings.</small>
          </div>
        </div>
        <div class='form-group row' style="display: none">
          <label class='col-sm-2 col-form-label' for='mintime'>Minimum time</label>
          <div class='col-sm-4'>
            <input id='mintime' class='form-control' type='text' name='mintime' aria-describedby='mintimeHelp' value='0'>
            <small id='mintimeHelp' class='form-text text-muted'>Minimum meter data time. Any earlier will be discarded.</small>
          </div>
          <label class='col-sm-2 col-form-label' for='maxtime'>Maximum time</label>
          <div class='col-sm-4'>
            <input id='maxtime' class='form-control' type='text' name='maxtime' aria-describedby='maxtimeHelp' value='2147483647000'>
            <small id='maxtimeHelp' class='form-text text-muted'>Maximum meter data time. Any later will be discarded.</small>
          </div>
        </div>
        <div class="form-group">
          <label for="ingest_timerange">Time range</label>
          <input id="ingest_timerange" type="text" class="form-control" aria-describedby="timerangeHelp" placeholder="Enter a time range for the ingest" value="">
          <small id="timerangeHelp" class="form-text text-muted">Enter the ingest start and end date/time. Any earlier or later will be discarded.</small>
        </div>
        <div class='form-group row'>
          <label class='col-sm-2 col-form-label' for='data_file'>Data file</label>
          <div class='col-sm-10'>
            <select id="data_file" class="form-control custom-select" name='data_file' aria-describedby="data_fileHelp">
            </select>
            <small id='data_fileHelp' class='form-text text-muted'>Data file to ingest.</small>
          </div>
        </div>
        <div class='form-group'>
          <button id='do_ingest' type='button' class='btn btn-primary'>Execute</button>
        </div>
      </form>
      <div id='ingest_results'>
      </div>
    </div>
  </div>
</div>
`;
            document.getElementById ("ingest").innerHTML = mustache.render (ingest_template);
            // see https://wireddots.com/products/datetimepicker
            const start = new Date (0);
            const end = new Date ();
            new DateRangePicker (
                "#ingest_timerange",
                {
                    timePicker: true,
                    timePickerIncrement: 15,
                    locale: {
                        format: 'YYYY.MM.DD HH:mm'
                    },
                    timePicker24Hour: true,
                    linkedCalendars: false,
                    startDate: start,
                    endDate: end,
                    showDropdowns: true
                    //showISOWeekNumbers: true
                },
                setDateRange
            );
            getFiles ();
            document.getElementById ("do_ingest").onclick = do_ingest;
        }

        /**
         * @summary Update the page.
         * @description Called if the page is already initialized and the page is again being shown.
         * @function focus
         * @memberOf module:cimingest
         */
        function focus ()
        {
        }

        /**
         * @summary Close down the page.
         * @description Called if the page is being hidden.
         * @function blur
         * @memberOf module:cimingest
         */
        function blur ()
        {
        }

        return (
            {
                initialize: initialize,
                focus: focus,
                blur: blur
            }
        );
    }
);