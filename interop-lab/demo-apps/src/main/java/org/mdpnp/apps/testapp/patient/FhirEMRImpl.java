package org.mdpnp.apps.testapp.patient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.composite.HumanNameDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.AdministrativeGenderEnum;
import ca.uhn.fhir.model.primitive.DateDt;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.IGenericClient;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.sql.DataSource;

import static ca.uhn.fhir.model.dstu2.valueset.AdministrativeGenderEnum.FEMALE;
import static ca.uhn.fhir.model.dstu2.valueset.AdministrativeGenderEnum.MALE;
import static ca.uhn.fhir.model.dstu2.valueset.IdentifierUseEnum.OFFICIAL;

/**
 * @author mfeinberg
 */
class FhirEMRImpl implements EMRFacade {

    private static final String HL7_ICE_URN_OID = "urn:oid:2.16.840.1.113883.3.1974";
    private FhirContext       fhirContext;
    private String            fhirURL;
    private final JdbcEMRImpl jdbcEMR;
    private final Executor collectionUpdateHandler;

    private final ObservableList<PatientInfo> patients = FXCollections.observableArrayList();

    public FhirEMRImpl(Executor executor) {
        this.collectionUpdateHandler = executor;
        this.jdbcEMR = new JdbcEMRImpl(executor);
    }

    public String getUrl() {
        return fhirURL;
    }
    public void setUrl(String url) {
        fhirURL = url;
    }
    public FhirContext getFhirContext() {
        return fhirContext;
    }
    public void setFhirContext(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public DataSource getDataSource() {
        return jdbcEMR.getDataSource();
    }
    public void setDataSource(DataSource ds) {
        jdbcEMR.setDataSource(ds);
    }

    @Override
    public void deleteDevicePatientAssociation(DevicePatientAssociation assoc) {
        jdbcEMR.deleteDevicePatientAssociation(assoc);
    }

    @Override
    public DevicePatientAssociation updateDevicePatientAssociation(DevicePatientAssociation assoc) {
        return jdbcEMR.updateDevicePatientAssociation(assoc);
    }

    @Override
    public ObservableList<PatientInfo> getPatients() {
        return patients;
    }

    @Override
    public void refresh() {

        IGenericClient fhirClient = getFhirClient();

        ca.uhn.fhir.model.api.Bundle bundle = fhirClient
                .search()
                .forResource(Patient.class)
                .execute();

        final List<PatientInfo> toRet = new ArrayList<>();
        List<Patient> patients = bundle.getResources(Patient.class);
        String official = ca.uhn.fhir.model.dstu2.valueset.IdentifierUseEnum.OFFICIAL.getCode();
        for(Patient p : patients) {
            IdentifierDt id = p.getIdentifierFirstRep();
            if(!HL7_ICE_URN_OID.equals(id.getSystem()))
                continue;
            String mrn = p.getIdentifierFirstRep().getValue();

            // now find the official name used on the record.
            for(HumanNameDt n : p.getName()) {
                if(official.equals(n.getUse()) || null == n.getUse()) {
                    String lName = n.getFamilyAsSingleString();
                    String fName = n.getGivenAsSingleString();
                    Date bDay = p.getBirthDate();
                    String g = p.getGender();
                    if(lName != null && fName != null && bDay != null && g != null) {
                        PatientInfo pi = new PatientInfo(mrn,fName,lName,fromFhire(g),bDay);
                        toRet.add(pi);
                        break;
                    }
                }
            }
        }
        collectionUpdateHandler.execute(() -> {
            patients.retainAll(toRet);
            Iterator<PatientInfo> itr = toRet.iterator();
            while (itr.hasNext()) {
                PatientInfo pi = itr.next();
                if (!patients.contains(pi)) {
                    this.patients.add(pi);
                }
            }
        });
    }

    public boolean createPatient(final PatientInfo p) {
        MethodOutcome mo = createPatientImpl(p);
        return mo.getCreated();
    }

    MethodOutcome createPatientImpl(final PatientInfo p) {
        collectionUpdateHandler.execute(() -> {
            patients.add(p);
        });

        IGenericClient fhirClient = getFhirClient();

        String mrnId = p.getMrn();

        Patient patient = new Patient();
        patient.addIdentifier().setUse(OFFICIAL).setSystem(HL7_ICE_URN_OID).setValue(mrnId);
        HumanNameDt name = patient.addName();
        name.addFamily(p.getLastName());
        name.addGiven(p.getFirstName());
        patient.setGender(toFhire(p.getGender()));
        DateDt dob = new DateDt(p.getDob());
        patient.setBirthDate(dob);

        MethodOutcome outcome = fhirClient.update()
                .resource(patient)
                .conditional()
                .where(Patient.IDENTIFIER.exactly().systemAndIdentifier(HL7_ICE_URN_OID, mrnId))
                .execute();

        return outcome;
    }

    IGenericClient getFhirClient() {
        return fhirContext.newRestfulGenericClient(fhirURL);
    }

    static AdministrativeGenderEnum toFhire(PatientInfo.Gender g) {
        switch (g) {
            default:
            case M: return MALE;
            case F: return FEMALE;
        }
    }

    static PatientInfo.Gender fromFhire(String g) {
        return fromFhire(AdministrativeGenderEnum.UNKNOWN.forCode(g));
    }

    static PatientInfo.Gender fromFhire(AdministrativeGenderEnum g) {
        switch (g) {
            default:     throw new IllegalArgumentException("Unknown conversion " + g);
            case MALE:   return PatientInfo.Gender.M;
            case FEMALE: return PatientInfo.Gender.F;
        }
    }
}
