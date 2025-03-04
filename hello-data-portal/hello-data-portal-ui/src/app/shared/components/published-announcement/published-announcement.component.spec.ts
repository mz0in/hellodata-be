///
/// Copyright © 2024, Kanton Bern
/// All rights reserved.
///
/// Redistribution and use in source and binary forms, with or without
/// modification, are permitted provided that the following conditions are met:
///     * Redistributions of source code must retain the above copyright
///       notice, this list of conditions and the following disclaimer.
///     * Redistributions in binary form must reproduce the above copyright
///       notice, this list of conditions and the following disclaimer in the
///       documentation and/or other materials provided with the distribution.
///     * Neither the name of the <organization> nor the
///       names of its contributors may be used to endorse or promote products
///       derived from this software without specific prior written permission.
///
/// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
/// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
/// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
/// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
/// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
/// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
/// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
/// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
/// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
/// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
///

import {ComponentFixture, TestBed} from '@angular/core/testing';
import {PublishedAnnouncementComponent} from './published-announcement.component';
import {Store} from '@ngrx/store';
import {AppState} from '../../../store/app/app.state';
import {beforeEach, describe, expect, it, jest} from '@jest/globals';
import {loadPublishedAnnouncements, markAnnouncementAsRead} from "../../../store/announcement/announcement.action";

describe('PublishedAnnouncementComponent', () => {
  let fixture: ComponentFixture<PublishedAnnouncementComponent>;
  let component: PublishedAnnouncementComponent;
  let store: Store<AppState>;

  const mockStore = {
    dispatch: jest.fn(),
    select: jest.fn(),
    pipe: jest.fn(),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [PublishedAnnouncementComponent],
      providers: [{provide: Store, useValue: mockStore}],
    });

    fixture = TestBed.createComponent(PublishedAnnouncementComponent);
    component = fixture.componentInstance;
    store = TestBed.inject(Store);
  });

  it('should create the PublishedAnnouncementComponent', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch LoadPublishedAnnouncements action on initialization', () => {
    fixture.detectChanges();
    expect(mockStore.dispatch).toHaveBeenCalledWith(loadPublishedAnnouncements());
  });

  it('should call hide() method and dispatch MarkAnnouncementAsRead action', () => {
    const announcement = {id: '1', published: true, message: 'Test Content'};

    jest.spyOn(component, 'hide');

    component.hide(announcement);

    expect(component.hide).toHaveBeenCalledWith(announcement);
    expect(mockStore.dispatch).toHaveBeenCalledWith(markAnnouncementAsRead({announcement}));
  });
});
