/*
 * Copyright 2010-2017 Norwegian Agency for Public Management and eGovernment (Difi)
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/community/eupl/og_page/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package no.sr.ringo.peppol;

import java.security.Principal;

/**
 * @author steinar
 *         Date: 11.11.2016
 *         Time: 11.34
 */
public class PeppolPrincipal implements Principal {
    private final String ap_name;

    public PeppolPrincipal(String ap_name) {
        this.ap_name = ap_name;
    }

    @Override
    public String getName() {
        return ap_name;
    }
}
