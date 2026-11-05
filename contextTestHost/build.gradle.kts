plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val stageProductionContext = tasks.register<Copy>("stageProductionContext") {
    from("../app/src/main/java/io/github/nelurea/muninn/data/context") {
        include("RoomContextSnapshotExporter.kt", "ContextRestorePreflight.kt", "ContextEmptyRoomRestorer.kt")
    }
    into(layout.buildDirectory.dir("generated/contextHost/main"))
}

val stageDatabaseEntities = tasks.register<Copy>("stageDatabaseEntities") {
    from("../app/src/main/java/io/github/nelurea/muninn/data/db") {
        include("*Entity.kt", "ImageRecord.kt", "DurableIdentityMigration.kt")
    }
    into(layout.buildDirectory.dir("generated/contextHost/database"))
}

val stageRestorePreflightTest = tasks.register<Copy>("stageRestorePreflightTest") {
    from("../app/src/androidTest/java/io/github/nelurea/muninn/data/context") {
        include("ContextRestorePreflightTest.kt", "ContextEmptyRoomRestorerTest.kt")
    }
    into(layout.buildDirectory.dir("generated/contextHost/androidTest"))
}

android {
    namespace = "io.github.nelurea.muninn.contexttesthost"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "io.github.nelurea.muninn.contexttesthost"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("main").kotlin.directories.add(
        layout.buildDirectory.get().asFile.resolve("generated/contextHost/database").absolutePath
    )
    sourceSets.getByName("main").kotlin.directories.add(
        layout.buildDirectory.get().asFile.resolve("generated/contextHost/main").absolutePath
    )
    sourceSets.getByName("androidTest").kotlin.directories.add(
        layout.buildDirectory.get().asFile.resolve("generated/contextHost/androidTest").absolutePath
    )
}

tasks.matching {
    it.name == "kspDebugKotlin" || it.name == "compileDebugKotlin"
}.configureEach {
    dependsOn(stageProductionContext, stageDatabaseEntities)
}

tasks.matching {
    it.name == "kspDebugAndroidTestKotlin" || it.name == "compileDebugAndroidTestKotlin"
}.configureEach {
    dependsOn(stageRestorePreflightTest)
}

dependencies {
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:core:1.6.1")
}
