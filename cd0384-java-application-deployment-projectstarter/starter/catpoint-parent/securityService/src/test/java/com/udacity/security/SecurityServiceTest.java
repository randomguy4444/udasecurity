package com.udacity.security;

import com.udacity.image.service.ImageService;
import com.udacity.security.data.*;
import com.udacity.security.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/**
 * Unit test for SecurityService.
 */
@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    private SecurityService securityService;

    private Sensor sensor;

    private final String random = UUID.randomUUID().toString();

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    private Set<Sensor> getAllSensors(int count, boolean status) {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < count; i++) {
            sensors.add(createSensor());
        }
        sensors.forEach(sensor -> sensor.setActive(status));

        return sensors;
    }

    private Sensor createSensor() {
        return new Sensor(random, SensorType.DOOR);
    }

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository,imageService);
        sensor = createSensor();
    }

    /**
     * 1.If alarm is armed and a sensor becomes activated, put the system into pending alarm status
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class,names = {"ARMED_HOME","ARMED_AWAY"})
    void handleSensorActivated_alarmArmed_pendingAlarm(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    /**
     * 2.If alarm is armed and a sensor becomes activated and the system is already pending alarm,
     * set the alarm status to alarm
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME","ARMED_AWAY"})
    void handleSensorActivated_alarmArmedAndPendingAlarm_alarmStatusToAlarm(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * 3.If pending alarm and all sensors are inactive, return to no alarm state
     */
    @Test
    void handleSensorDeactivated_pendingAlarmAndSensorsInactive_returnToNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(false);

        securityService.changeSensorActivationStatus(sensor);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * 4.If alarm is active, change in sensor state should not affect the alarm state
     */
    @ParameterizedTest
    @ValueSource(booleans = {true,false})
    void changeSensorStatus_alarmActive_notAffectAlarmState(boolean active) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        securityService.changeSensorActivationStatus(sensor, active);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    /**
     * 5.If a sensor is activated while already active and the system is in pending state,
     * change it to alarm state
     */
    @Test
    void activatedSensor_sensorActiveAndPendingSystem_changeToAlarmState() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * 6.If a sensor is deactivated while already inactive,
     * make no changes to the alarm state
     */
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM"})
    void deactivateSensor_sensorInactive_notAffectAlarmState(AlarmStatus status) {
        when(securityRepository.getAlarmStatus()).thenReturn(status);
        sensor.setActive(false);

        securityService.changeSensorActivationStatus(sensor,false);

        verify(securityRepository,never()).setAlarmStatus(any(AlarmStatus.class));
    }

    /**
     * 7.If the image service identifies an image containing a cat while the system is armed-home,
     * put the system into alarm status
     */
    @Test
    void catDetected_armedHomeSystem_changeToAlarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);

        securityService.processImage(mock(BufferedImage.class));

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    /**
     * 8.If the image service identifies an image that does not contain a cat,
     * change the status to no alarm as long as the sensors are not active
     */
    @Test
    void catNotDetected_sensorsInactive_changeToNoAlarm() {
        Set<Sensor> sensors = getAllSensors(3, false);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(BufferedImage.class),anyFloat())).thenReturn(false);

        securityService.processImage(mock(BufferedImage.class));

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * 9.If the system is disarmed, set the status to no alarm
     */
    @Test
    void setArmingStatus_disarmSystem_changeToNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /**
     * 10.If the system is armed, reset all sensors to inactive
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class,names = {"ARMED_HOME","ARMED_AWAY"})
    void setArmingStatus_armSystem_resetSensorsToInactive(ArmingStatus armingStatus) {
        Set<Sensor> sensors = getAllSensors(3,true);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        securityService.setArmingStatus(armingStatus);

        securityService.getSensors().forEach(sensor ->
                assertFalse(sensor.getActive())
        );
    }

    /**
     * 11.If the system is armed-home while the camera shows a cat,
     * set the alarm status to alarm
     */
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class,names = {"ARMED_AWAY","DISARMED"})
    void armedHomeSystem_cameraShowsCat_changeToAlarmStatus(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);

        securityService.processImage(mock(BufferedImage.class));
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // full coverage
    /**
     * If alarm is active and the system is disarmed, put the system into pending alarm status
     */
    @Test
    void alarmActiveAndSystemDisarmed_changeToPending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        securityService.changeSensorActivationStatus(sensor);

        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    /**
     * If a sensor is deactivated while active and the system is already pending alarm,
     * return to no alarm state
     */
    @Test
    void handleSensorDeactivated_sensorActiveAndSystemPending_returnToNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);

        securityService.changeSensorActivationStatus(sensor,false);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }
}
