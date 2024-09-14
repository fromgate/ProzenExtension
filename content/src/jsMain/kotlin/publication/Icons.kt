package publication

enum class Icons (val cssClass: String) {
    VIEWS("publication_icon_views_2"), // страничка с двумя строчками
    VIEW_VIDEO ("publication_icon_views_video"),
    FULL_VIEWS("publication_icon_full_views"), // страничка с галочкой
    CIRCLE("publication_icon_views"), // жирный кружок, использовался как просмотры?
    CLOCK ("publication_icon_read_time"), //часы
    LINK ("publication_icon_short_url"),
    SAD_ROBOT("publication_icon_sad_robot");
}
