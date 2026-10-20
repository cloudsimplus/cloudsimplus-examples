/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2021 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
/// A set of examples showing how to run synchronous simulations.
/// They use the [org.cloudsimplus.core.CloudSimPlus#startSync()]
/// and then the [org.cloudsimplus.core.CloudSimPlus#runFor(double)]
/// to run the simulation for a given period o time.
/// This way, the simulation can be executed inside a loop, where
/// different operations can be performed in each iteration
/// (such as data collection).
///
/// @author Pawel Koperek
/// @author Manoel Campos da Silva Filho
@NullMarked
package org.cloudsimplus.examples.synchronous;

import org.jspecify.annotations.NullMarked;
