# Reglas de R8 propias de ApunteScroll. Por ahora la versión release no se minifica
# (isMinifyEnabled = false; se activa en P1). AGP 9 exige que este archivo exista igual.
# Estas reglas ya se conocen y no hacen nada mientras R8 esté apagado.

# PdfBox-Android sin bcpkix/bcutil (excluidos a propósito, ver app/build.gradle.kts):
# solo los usa el cifrado con certificado, que PdfBox rechaza antes de llegar a esas clases.
-dontwarn org.bouncycastle.cms.**
-dontwarn org.bouncycastle.asn1.cms.**
-dontwarn org.bouncycastle.cert.**

# Decodificador JPEG 2000 opcional de PdfBox-Android (no se incluye).
-dontwarn com.gemalto.jp2.**

# bcprov: almacén LDAP (javax.naming no existe en Android).
-dontwarn javax.naming.**
