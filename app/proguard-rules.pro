# Минимальные правила для release-сборки Фудкортницы.

# Активности и ContentProvider объявлены в манифесте, R8 их сохраняет,
# но оставляем явные правила на случай агрессивного(full mode) сжатия.
-keep public class net.potatom.foodkortniza.MainActivity
-keep public class net.potatom.foodkortniza.CatalogActivity
-keep public class net.potatom.foodkortniza.TabActivity
-keep public class net.potatom.foodkortniza.RegistryProvider

# Custom View создаётся из XML.
-keep public class net.potatom.foodkortniza.ui.BottomBarView {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Читаемые stack traces без заметного влияния на размер.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
