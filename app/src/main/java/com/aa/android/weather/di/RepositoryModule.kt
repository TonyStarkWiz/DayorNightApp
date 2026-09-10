package com.aa.android.weather.di

import android.content.Context
import androidx.room.Room
import com.aa.android.geocoder.geocoder.Geocoder
import com.aa.android.weather.api.WeatherApi
import com.aa.android.weather.api.WeatherRemoteDataSource
import com.aa.android.weather.db.ForecastDao
import com.aa.android.weather.db.WeatherDatabase
import com.aa.android.weather.dispatchers.AppDispatcherProvider
import com.aa.android.weather.dispatchers.DispatcherProvider
import com.aa.android.weather.error.ErrorMapper
import com.aa.android.weather.network.ConnectivityNetworkChecker
import com.aa.android.weather.network.NetworkChecker
import com.aa.android.weather.repository.CityLocation
import com.aa.android.weather.repository.CityLookup
import com.aa.android.weather.repository.WeatherRepository
import com.aa.android.weather.repository.WeatherRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    fun provideDispatcherProvider(): DispatcherProvider = AppDispatcherProvider()

    @Provides
    fun provideErrorMapper(): ErrorMapper = ErrorMapper()

    @Provides
    fun provideNetworkChecker(@ApplicationContext context: Context): NetworkChecker {
        return ConnectivityNetworkChecker(context)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeatherDatabase {
        return Room.databaseBuilder(context, WeatherDatabase::class.java, "weather.db").build()
    }

    @Provides
    fun provideForecastDao(database: WeatherDatabase): ForecastDao = database.forecastDao()

    @Provides
    fun provideGeocoder(): Geocoder = Geocoder()

    @Provides
    fun provideCityLookup(geocoder: Geocoder): CityLookup {
        return CityLookup { cityName ->
            val city = geocoder.getCityInfo(cityName).firstOrNull() ?: return@CityLookup null
            CityLocation(city.name, city.latitude, city.longitude)
        }
    }

    @Provides
    fun provideRemoteDataSource(
        weatherApi: WeatherApi,
        errorMapper: ErrorMapper
    ): WeatherRemoteDataSource {
        return WeatherRemoteDataSource(weatherApi, errorMapper)
    }

    @Provides
    fun provideWeatherRepository(
        remoteDataSource: WeatherRemoteDataSource,
        cityLookup: CityLookup,
        forecastDao: ForecastDao,
        networkChecker: NetworkChecker,
        dispatchers: DispatcherProvider,
        errorMapper: ErrorMapper
    ): WeatherRepository {
        return WeatherRepositoryImpl(
            remoteDataSource,
            cityLookup,
            forecastDao,
            networkChecker,
            dispatchers,
            errorMapper
        )
    }
}
